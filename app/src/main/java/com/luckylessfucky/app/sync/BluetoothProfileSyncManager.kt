package com.luckylessfucky.app.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.luckylessfucky.app.domain.model.ProfileExportPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class BluetoothDeviceCandidate(
    val name: String,
    val address: String,
    val isPaired: Boolean
)

sealed interface BluetoothSyncState {
    data object Idle : BluetoothSyncState
    data class Hosting(val profileName: String) : BluetoothSyncState
    data class Connecting(val targetDeviceName: String) : BluetoothSyncState
    data class Success(val message: String, val payload: ProfileExportPayload?) : BluetoothSyncState
    data class Error(val message: String) : BluetoothSyncState
}

class BluetoothProfileSyncManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _syncState = MutableStateFlow<BluetoothSyncState>(BluetoothSyncState.Idle)
    val syncState: StateFlow<BluetoothSyncState> = _syncState.asStateFlow()

    private val _devices = MutableStateFlow<List<BluetoothDeviceCandidate>>(emptyList())
    val devices: StateFlow<List<BluetoothDeviceCandidate>> = _devices.asStateFlow()

    private var activeServerSocket: BluetoothServerSocket? = null
    private var activeClientSocket: BluetoothSocket? = null
    private var discoveryReceiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (device != null && !device.address.isNullOrBlank()) {
                    val candidate = BluetoothDeviceCandidate(
                        name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?: "Device (${device.address.takeLast(5)})",
                        address = device.address,
                        isPaired = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                    )
                    _devices.update { current ->
                        if (current.any { it.address == candidate.address }) current else current + candidate
                    }
                }
            }
        }
    }

    fun isBluetoothAvailableAndEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedAndDiscoverDevices() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _syncState.value = BluetoothSyncState.Error("Bluetooth is disabled or unavailable on this device.")
            return
        }

        val pairedList = runCatching {
            adapter.bondedDevices?.map { device ->
                BluetoothDeviceCandidate(
                    name = device.name?.takeIf { it.isNotBlank() } ?: "Paired (${device.address.takeLast(5)})",
                    address = device.address,
                    isPaired = true
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())

        _devices.value = pairedList

        runCatching {
            if (!discoveryReceiverRegistered) {
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                context.registerReceiver(discoveryReceiver, filter)
                discoveryReceiverRegistered = true
            }
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        runCatching {
            if (discoveryReceiverRegistered) {
                context.unregisterReceiver(discoveryReceiver)
                discoveryReceiverRegistered = false
            }
            bluetoothAdapter?.cancelDiscovery()
        }
    }

    /**
     * Hosts an RFCOMM server socket so another device can download or update this profile over Bluetooth.
     */
    @SuppressLint("MissingPermission")
    suspend fun hostProfileForDownload(payload: ProfileExportPayload) = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _syncState.value = BluetoothSyncState.Error("Bluetooth is turned off.")
            return@withContext
        }

        closeActiveSockets()
        _syncState.value = BluetoothSyncState.Hosting(payload.username)

        try {
            val serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            activeServerSocket = serverSocket

            val clientSocket = serverSocket.accept()
            activeClientSocket = clientSocket

            clientSocket.use { socket ->
                ProfileJsonCodec.writeFramedPayload(payload, socket.outputStream)
            }
            _syncState.value = BluetoothSyncState.Success(
                message = "Sent profile '${payload.username}' (${payload.recordedVariantIds.size} Pokémon) via Bluetooth.",
                payload = null
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (isActive) {
                _syncState.value = BluetoothSyncState.Error(
                    "Bluetooth broadcast stopped: ${e.localizedMessage ?: "Connection closed"}"
                )
            }
        } finally {
            closeActiveSockets()
        }
    }

    /**
     * Connects to a remote device hosting LuckyLessFucky and downloads/updates the profile payload.
     */
    @SuppressLint("MissingPermission")
    suspend fun downloadOrUpdateProfileFromDevice(
        candidate: BluetoothDeviceCandidate,
        onPayloadReceived: suspend (ProfileExportPayload) -> Unit
    ) = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _syncState.value = BluetoothSyncState.Error("Bluetooth is turned off.")
            return@withContext
        }

        stopDiscovery()
        closeActiveSockets()
        _syncState.value = BluetoothSyncState.Connecting(candidate.name)

        try {
            val remoteDevice = adapter.getRemoteDevice(candidate.address)
            val socket = remoteDevice.createRfcommSocketToServiceRecord(SERVICE_UUID)
            activeClientSocket = socket

            socket.connect()
            val payload = socket.use { connectedSocket ->
                ProfileJsonCodec.readFramedPayload(connectedSocket.inputStream).getOrThrow()
            }

            onPayloadReceived(payload)
            _syncState.value = BluetoothSyncState.Success(
                message = "Downloaded & updated profile '${payload.username}' (${payload.recordedVariantIds.size} Pokémon).",
                payload = payload
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            _syncState.value = BluetoothSyncState.Error(
                "Failed to download profile from ${candidate.name}: ${e.localizedMessage ?: "Connection error"}"
            )
        } finally {
            closeActiveSockets()
        }
    }

    fun resetState() {
        closeActiveSockets()
        _syncState.value = BluetoothSyncState.Idle
    }

    fun closeActiveSockets() {
        runCatching { activeClientSocket?.close() }
        runCatching { activeServerSocket?.close() }
        activeClientSocket = null
        activeServerSocket = null
    }

    companion object {
        const val SERVICE_NAME = "LuckyLessFuckyProfileSync"
        val SERVICE_UUID: UUID = UUID.fromString("8f3b9d10-4c2a-4e8f-9a1b-7d5e3c2b1a09")
    }
}
