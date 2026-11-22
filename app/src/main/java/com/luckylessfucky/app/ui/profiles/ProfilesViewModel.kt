package com.luckylessfucky.app.ui.profiles

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckylessfucky.app.data.repository.PokedexRepository
import com.luckylessfucky.app.data.repository.ProfileRepository
import com.luckylessfucky.app.domain.model.PokedexItem
import com.luckylessfucky.app.domain.model.ProfileExportPayload
import com.luckylessfucky.app.domain.model.TradeProfile
import com.luckylessfucky.app.sync.BluetoothDeviceCandidate
import com.luckylessfucky.app.sync.BluetoothProfileSyncManager
import com.luckylessfucky.app.sync.BluetoothSyncState
import com.luckylessfucky.app.sync.FileImportExportHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<TradeProfile> = emptyList(),
    val activeTargetProfile: TradeProfile? = null,
    val totalCatalogVariantsCount: Int = 0,
    val inspectedProfile: TradeProfile? = null,
    val inspectedProfilePokedex: List<PokedexItem> = emptyList(),
    val statusMessage: String? = null,
    val bluetoothSyncState: BluetoothSyncState = BluetoothSyncState.Idle,
    val bluetoothDevices: List<BluetoothDeviceCandidate> = emptyList()
)

class ProfilesViewModel(
    private val profileRepository: ProfileRepository,
    private val pokedexRepository: PokedexRepository,
    private val fileHelper: FileImportExportHelper,
    private val bluetoothSyncManager: BluetoothProfileSyncManager
) : ViewModel() {

    private val inspectedProfileIdFlow = MutableStateFlow<String?>(null)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private var pendingExportPayload: ProfileExportPayload? = null
    private var bluetoothJob: Job? = null

    val uiState: StateFlow<ProfilesUiState> = combine(
        profileRepository.tradeProfilesFlow,
        profileRepository.activeTargetProfileFlow,
        pokedexRepository.allVariantsFlow,
        inspectedProfileIdFlow,
        combine(
            statusMessageFlow,
            bluetoothSyncManager.syncState,
            bluetoothSyncManager.devices
        ) { msg, btState, btDevices -> Triple(msg, btState, btDevices) }
    ) { profiles, activeTarget, allVariants, inspectedId, (statusMsg, btState, btDevices) ->
        val inspected = profiles.firstOrNull { it.profileId == inspectedId }
        val inspectedItems = if (inspected != null) {
            allVariants.map { variant ->
                PokedexItem(
                    variant = variant,
                    isRecorded = inspected.recordedVariantIds.contains(variant.variantId)
                )
            }
        } else {
            emptyList()
        }

        ProfilesUiState(
            profiles = profiles,
            activeTargetProfile = activeTarget,
            totalCatalogVariantsCount = allVariants.size,
            inspectedProfile = inspected,
            inspectedProfilePokedex = inspectedItems,
            statusMessage = statusMsg,
            bluetoothSyncState = btState,
            bluetoothDevices = btDevices
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfilesUiState()
    )

    fun setLuckyTradeTarget(profileId: String?) {
        viewModelScope.launch {
            profileRepository.setActiveLuckyTradeTarget(profileId)
            statusMessageFlow.value = if (profileId == null) {
                "Cleared Lucky Trade Target (None selected)."
            } else {
                "Updated current Lucky Trade Target."
            }
        }
    }

    fun inspectProfilePokedex(profileId: String?) {
        inspectedProfileIdFlow.value = profileId
    }

    fun deleteProfile(profile: TradeProfile) {
        viewModelScope.launch {
            if (inspectedProfileIdFlow.value == profile.profileId) {
                inspectedProfileIdFlow.value = null
            }
            profileRepository.deleteTradeTargetProfile(profile.profileId)
            statusMessageFlow.value = "Removed trade profile '${profile.username}'."
        }
    }

    fun importTargetProfileFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = fileHelper.importPayloadFromUri(uri)
            result.onSuccess { payload ->
                val saved = profileRepository.importOrUpdateTradeTargetProfile(payload)
                statusMessageFlow.value =
                    "Imported Trade Target profile '${saved.username}' (${saved.recordedVariantIds.size} Not Needed)."
            }.onFailure { err ->
                statusMessageFlow.value =
                    "Import failed: ${err.localizedMessage ?: "Invalid profile file"}"
            }
        }
    }

    fun prepareExportTargetProfile(
        profile: TradeProfile,
        launchCreateDocument: (suggestedFileName: String) -> Unit
    ) {
        viewModelScope.launch {
            val payload = profileRepository.buildTargetExportPayload(profile.profileId)
            if (payload != null) {
                pendingExportPayload = payload
                launchCreateDocument(FileImportExportHelper.suggestedFileName(profile.username))
            } else {
                statusMessageFlow.value = "Unable to find profile '${profile.username}' for export."
            }
        }
    }

    fun completeExportTargetProfileToUri(uri: Uri) {
        val payload = pendingExportPayload ?: return
        pendingExportPayload = null
        viewModelScope.launch {
            fileHelper.exportPayloadToUri(uri, payload)
                .onSuccess {
                    statusMessageFlow.value =
                        "Exported Trade Target profile '${payload.username}'."
                }
                .onFailure { err ->
                    statusMessageFlow.value =
                        "Export failed: ${err.localizedMessage ?: "Write error"}"
                }
        }
    }

    fun startBluetoothDiscovery() {
        bluetoothSyncManager.refreshPairedAndDiscoverDevices()
    }

    fun stopBluetoothDiscovery() {
        bluetoothSyncManager.stopDiscovery()
    }

    fun hostUserProfileViaBluetooth() {
        bluetoothJob?.cancel()
        bluetoothJob = viewModelScope.launch {
            val payload = profileRepository.buildUserExportPayload()
            bluetoothSyncManager.hostProfileForDownload(payload)
        }
    }

    fun downloadOrUpdateTargetProfileViaBluetooth(device: BluetoothDeviceCandidate) {
        bluetoothJob?.cancel()
        bluetoothJob = viewModelScope.launch {
            bluetoothSyncManager.downloadOrUpdateProfileFromDevice(device) { payload ->
                profileRepository.importOrUpdateTradeTargetProfile(payload)
            }
        }
    }

    fun resetBluetoothState() {
        bluetoothJob?.cancel()
        bluetoothSyncManager.stopDiscovery()
        bluetoothSyncManager.resetState()
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    companion object {
        fun factory(
            profileRepository: ProfileRepository,
            pokedexRepository: PokedexRepository,
            fileHelper: FileImportExportHelper,
            bluetoothSyncManager: BluetoothProfileSyncManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfilesViewModel(
                    profileRepository = profileRepository,
                    pokedexRepository = pokedexRepository,
                    fileHelper = fileHelper,
                    bluetoothSyncManager = bluetoothSyncManager
                ) as T
            }
        }
    }
}
