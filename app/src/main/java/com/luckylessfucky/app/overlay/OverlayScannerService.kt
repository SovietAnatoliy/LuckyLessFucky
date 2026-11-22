package com.luckylessfucky.app.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.luckylessfucky.app.LuckyLessFuckyApp
import com.luckylessfucky.app.R
import com.luckylessfucky.app.scanner.PokemonScreenAnalyzer
import com.luckylessfucky.app.scanner.ScreenCaptureManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverlayScannerService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    private lateinit var windowManager: WindowManager
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var screenAnalyzer: PokemonScreenAnalyzer
    private lateinit var overlayStateHolder: OverlayStateHolder

    private var overlayView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)

        val app = application as LuckyLessFuckyApp
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)
        screenAnalyzer = PokemonScreenAnalyzer(
            pokedexRepository = app.pokedexRepository,
            screenCaptureManager = screenCaptureManager
        )

        overlayStateHolder = OverlayStateHolder(
            scope = lifecycleScope,
            performScan = { screenAnalyzer.scanCurrentScreen() },
            addVariantToUserPokedex = { variantId ->
                app.pokedexRepository.addPokemonToUserPokedex(variantId)
            }
        )

        // Observe active Lucky Trade Target profile
        lifecycleScope.launch {
            app.profileRepository.activeTargetProfileFlow.collectLatest { profile ->
                overlayStateHolder.updateActiveTargetProfile(profile)
            }
        }

        // Observe 2-second Auto-Scan setting
        lifecycleScope.launch {
            app.preferencesRepository.settingsFlow.collectLatest { settings ->
                overlayStateHolder.updateAutoScanEnabled(settings.isAutoScanEnabled)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopOverlayAndService()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }

        if (resultCode == Activity.RESULT_OK && projectionData != null) {
            screenCaptureManager.initialize(resultCode, projectionData)
        }

        if (Settings.canDrawOverlays(this)) {
            showFloatingOverlayIfNeeded()
        }

        return START_STICKY
    }

    private fun showFloatingOverlayIfNeeded() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 220
        }
        windowLayoutParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayScannerService)
            setViewTreeViewModelStoreOwner(this@OverlayScannerService)
            setViewTreeSavedStateRegistryOwner(this@OverlayScannerService)
            setContent {
                val state by overlayStateHolder.uiState.collectAsState()
                OverlayFloatingWidget(
                    uiState = state,
                    onScanAndAddClick = {
                        overlayStateHolder.onScanAndAddToUserPokedexClicked()
                    },
                    onManualTargetCheckClick = {
                        overlayStateHolder.onManualTargetCheckClicked()
                    },
                    onCloseOverlayClick = {
                        stopOverlayAndService()
                    },
                    onDragOverlay = { dx, dy ->
                        val lp = windowLayoutParams ?: return@OverlayFloatingWidget
                        lp.x += dx.roundToInt()
                        lp.y += dy.roundToInt()
                        runCatching {
                            windowManager.updateViewLayout(this, lp)
                        }
                    }
                )
            }
        }

        runCatching {
            windowManager.addView(composeView, params)
            overlayView = composeView
        }
    }

    private fun stopOverlayAndService() {
        val app = application as LuckyLessFuckyApp
        lifecycleScope.launch {
            app.preferencesRepository.setOverlayEnabled(false)
        }
        removeFloatingOverlay()
        screenCaptureManager.release()
        overlayStateHolder.stopAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeFloatingOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeFloatingOverlay()
        screenCaptureManager.release()
        overlayStateHolder.stopAll()
        store.clear()
        super.onDestroy()
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = "luckylessfucky_overlay_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_OVERLAY = "com.luckylessfucky.app.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.luckylessfucky.app.action.STOP_OVERLAY"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        private const val NOTIFICATION_ID = 4201
    }
}
