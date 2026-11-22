package com.luckylessfucky.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luckylessfucky.app.overlay.OverlayScannerService
import com.luckylessfucky.app.ui.pokedex.PokedexScreen
import com.luckylessfucky.app.ui.pokedex.PokedexViewModel
import com.luckylessfucky.app.ui.profiles.BluetoothSyncSheet
import com.luckylessfucky.app.ui.profiles.ProfilesScreen
import com.luckylessfucky.app.ui.profiles.ProfilesViewModel
import com.luckylessfucky.app.ui.settings.SettingsScreen
import com.luckylessfucky.app.ui.settings.SettingsViewModel

private enum class AppTab(val label: String, val icon: ImageVector) {
    POKEDEX("Pokédex", Icons.Default.CatchingPokemon),
    PROFILES("Trade Targets", Icons.Default.Group),
    SETTINGS("Settings", Icons.Default.Settings)
}

private val LuckyColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F)
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LuckyLessFuckyApp

        setContent {
            MaterialTheme(colorScheme = LuckyColorScheme) {
                LuckyLessFuckyMainContent(
                    app = app,
                    onRequestStartOverlayService = { resultCode, data ->
                        startOverlayService(resultCode, data)
                    },
                    onRequestStopOverlayService = {
                        stopOverlayService()
                    }
                )
            }
        }
    }

    private fun startOverlayService(resultCode: Int, projectionData: Intent) {
        val serviceIntent = Intent(this, OverlayScannerService::class.java).apply {
            action = OverlayScannerService.ACTION_START_OVERLAY
            putExtra(OverlayScannerService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayScannerService.EXTRA_PROJECTION_DATA, projectionData)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopOverlayService() {
        val stopIntent = Intent(this, OverlayScannerService::class.java).apply {
            action = OverlayScannerService.ACTION_STOP_OVERLAY
        }
        startService(stopIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuckyLessFuckyMainContent(
    app: LuckyLessFuckyApp,
    onRequestStartOverlayService: (Int, Intent) -> Unit,
    onRequestStopOverlayService: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.POKEDEX) }
    var isBluetoothSheetOpen by remember { mutableStateOf(false) }

    val pokedexViewModel: PokedexViewModel = viewModel(
        factory = PokedexViewModel.factory(
            pokedexRepository = app.pokedexRepository,
            profileRepository = app.profileRepository
        )
    )
    val profilesViewModel: ProfilesViewModel = viewModel(
        factory = ProfilesViewModel.factory(
            profileRepository = app.profileRepository,
            pokedexRepository = app.pokedexRepository,
            fileHelper = app.fileImportExportHelper,
            bluetoothSyncManager = app.bluetoothSyncManager
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            preferencesRepository = app.preferencesRepository,
            pokedexRepository = app.pokedexRepository,
            profileRepository = app.profileRepository,
            fileHelper = app.fileImportExportHelper
        )
    )

    val pokedexState by pokedexViewModel.uiState.collectAsState()
    val profilesState by profilesViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()

    // SAF File Launchers for User Profile (Settings)
    val exportUserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            settingsViewModel.completeExportUserProfileToUri(uri)
        }
    }
    val importUserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            settingsViewModel.importUserProfileFromUri(uri)
        }
    }

    // SAF File Launchers for Trade Target Profile (Trade Targets Tab)
    val exportTargetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            profilesViewModel.completeExportTargetProfileToUri(uri)
        }
    }
    val importTargetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            profilesViewModel.importTargetProfileFromUri(uri)
        }
    }

    // MediaProjection Launcher for Overlay Mode Screen Scanner
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            settingsViewModel.setOverlayEnabled(true)
            onRequestStartOverlayService(result.resultCode, data)
        } else {
            settingsViewModel.setOverlayEnabled(false)
        }
    }

    // Overlay Permission Settings Launcher
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(app)) {
            val mpManager =
                app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            settingsViewModel.setOverlayEnabled(false)
        }
    }

    // Bluetooth Runtime Permission Launcher
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isBluetoothSheetOpen = true
        profilesViewModel.startBluetoothDiscovery()
    }

    val handleOverlayToggle: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (!Settings.canDrawOverlays(app)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${app.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                val mpManager =
                    app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
            }
        } else {
            settingsViewModel.setOverlayEnabled(false)
            onRequestStopOverlayService()
        }
    }

    val handleOpenBluetoothSheet: () -> Unit = {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        bluetoothPermissionLauncher.launch(perms)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LuckyLessFucky",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    val isOverlayOn = settingsState.settings.isOverlayEnabled
                    FilterChip(
                        selected = isOverlayOn,
                        onClick = { handleOverlayToggle(!isOverlayOn) },
                        label = {
                            Text(if (isOverlayOn) "Overlay: ON" else "Overlay: OFF")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle Overlay Mode"
                            )
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.POKEDEX -> {
                    PokedexScreen(
                        uiState = pokedexState,
                        onSearchQueryChanged = pokedexViewModel::onSearchQueryChanged,
                        onFilterSelected = pokedexViewModel::onFilterSelected,
                        onTogglePokemon = pokedexViewModel::onTogglePokemon
                    )
                }
                AppTab.PROFILES -> {
                    ProfilesScreen(
                        uiState = profilesState,
                        onSetActiveTarget = profilesViewModel::setLuckyTradeTarget,
                        onImportTargetProfileClick = {
                            importTargetLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onExportTargetProfileClick = { profile ->
                            profilesViewModel.prepareExportTargetProfile(profile) { suggestedName ->
                                exportTargetLauncher.launch(suggestedName)
                            }
                        },
                        onOpenBluetoothSyncClick = handleOpenBluetoothSheet,
                        onInspectProfileClick = { profile ->
                            profilesViewModel.inspectProfilePokedex(profile.profileId)
                        },
                        onDismissInspectProfile = {
                            profilesViewModel.inspectProfilePokedex(null)
                        },
                        onDeleteProfileClick = profilesViewModel::deleteProfile,
                        onDismissStatusMessage = profilesViewModel::clearStatusMessage
                    )
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(
                        uiState = settingsState,
                        onSaveUsername = settingsViewModel::saveUsername,
                        onExportMyProfileClick = {
                            settingsViewModel.prepareExportUserProfile { suggestedName ->
                                exportUserLauncher.launch(suggestedName)
                            }
                        },
                        onImportMyProfileClick = {
                            importUserLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onToggleOverlayMode = handleOverlayToggle,
                        onToggleAutoScanTwoSeconds = settingsViewModel::setAutoScanTwoSecondIntervalEnabled,
                        onDismissStatusMessage = settingsViewModel::clearStatusMessage
                    )
                }
            }
        }

        if (isBluetoothSheetOpen) {
            BluetoothSyncSheet(
                syncState = profilesState.bluetoothSyncState,
                devices = profilesState.bluetoothDevices,
                onRefreshDevices = profilesViewModel::startBluetoothDiscovery,
                onHostMyProfile = profilesViewModel::hostUserProfileViaBluetooth,
                onConnectAndDownload = profilesViewModel::downloadOrUpdateTargetProfileViaBluetooth,
                onCancelOrReset = profilesViewModel::resetBluetoothState,
                onDismiss = {
                    profilesViewModel.resetBluetoothState()
                    isBluetoothSheetOpen = false
                }
            )
        }
    }
}
