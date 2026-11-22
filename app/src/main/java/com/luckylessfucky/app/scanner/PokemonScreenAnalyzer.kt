package com.luckylessfucky.app.scanner

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.luckylessfucky.app.data.repository.PokedexRepository
import com.luckylessfucky.app.domain.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PokemonScreenAnalyzer(
    private val pokedexRepository: PokedexRepository,
    private val screenCaptureManager: ScreenCaptureManager
) {
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun scanCurrentScreen(): ScanResult = withContext(Dispatchers.Default) {
        if (!screenCaptureManager.isReady) {
            return@withContext ScanResult.Inconclusive(
                reason = "Screen capture permission not active"
            )
        }

        val bitmap = screenCaptureManager.captureLatestFrame()
            ?: return@withContext ScanResult.Inconclusive(
                reason = "Unable to capture screen frame"
            )

        try {
            analyzeBitmap(bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    suspend fun analyzeBitmap(bitmap: Bitmap): ScanResult = withContext(Dispatchers.Default) {
        runCatching {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = textRecognizer.process(inputImage).await()
            val lines = visionText.textBlocks.flatMap { block ->
                block.lines.map { it.text }
            }
            val catalog = pokedexRepository.getAllVariants()
            VariantMatcher.matchScreenText(
                ocrLines = lines,
                catalog = catalog,
                detectedSpriteSignatureHex = null
            )
        }.getOrElse { error ->
            ScanResult.Inconclusive(
                reason = "OCR error: ${error.localizedMessage ?: "Failed to read screen"}"
            )
        }
    }
}
