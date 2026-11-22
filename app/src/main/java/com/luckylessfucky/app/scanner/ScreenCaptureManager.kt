package com.luckylessfucky.app.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private var screenDensity: Int = DisplayMetrics.DENSITY_HIGH

    val isReady: Boolean
        get() = mediaProjection != null && imageReader != null

    fun initialize(resultCode: Int, data: Intent) {
        release()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // Downscale slightly if resolution is very high to keep OCR fast & low-memory
        val scale = if (metrics.widthPixels > 1080) 1080f / metrics.widthPixels else 1f
        screenWidth = (metrics.widthPixels * scale).toInt().coerceAtLeast(360)
        screenHeight = (metrics.heightPixels * scale).toInt().coerceAtLeast(640)
        screenDensity = metrics.densityDpi

        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data) ?: return

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
            }
        }, null)

        val reader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        val vDisplay = projection.createVirtualDisplay(
            "LuckyLessFuckyScannerDisplay",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )

        mediaProjection = projection
        imageReader = reader
        virtualDisplay = vDisplay
    }

    suspend fun captureLatestFrame(): Bitmap? = withContext(Dispatchers.IO) {
        val reader = imageReader ?: return@withContext null

        var image = reader.acquireLatestImage()
        if (image == null) {
            delay(60L)
            image = reader.acquireLatestImage()
        }
        if (image == null) return@withContext null

        image.use { img ->
            val plane = img.planes.firstOrNull() ?: return@use null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * img.width

            val paddedWidth = img.width + rowPadding / pixelStride
            val fullBitmap = Bitmap.createBitmap(paddedWidth, img.height, Bitmap.Config.ARGB_8888)
            fullBitmap.copyPixelsFromBuffer(buffer)

            if (paddedWidth == img.width) {
                fullBitmap
            } else {
                val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, img.width, img.height)
                fullBitmap.recycle()
                cropped
            }
        }
    }

    fun release() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { mediaProjection?.stop() }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
