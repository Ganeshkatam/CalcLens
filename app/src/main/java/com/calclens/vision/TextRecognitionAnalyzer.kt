package com.calclens.vision

import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TextRecognitionAnalyzer(
    private val onCandidatesDetected: (List<VisionCandidate>) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isBusy = AtomicBoolean(false)
    private val idCounter = AtomicInteger(0)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isBusy.get()) {
            imageProxy.close()
            return
        }

        isBusy.set(true)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val imageWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val imageHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val candidates = mutableListOf<VisionCandidate>()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val rawText = line.text
                        val normalized = ExpressionNormalizer.normalize(rawText)

                        if (MathRegionFilter.isViableArithmetic(normalized, confidence = line.confidence ?: 0.9f)) {
                            val box = line.boundingBox
                            val normalizedBox = if (box != null) {
                                normalizeBoundingBox(box, imageWidth, imageHeight)
                            } else {
                                RectF(0.2f, 0.4f, 0.6f, 0.1f)
                            }

                            candidates.add(
                                VisionCandidate(
                                    id = "cand-${idCounter.incrementAndGet()}",
                                    rawText = rawText,
                                    normalizedText = normalized,
                                    boundingBox = normalizedBox,
                                    confidence = line.confidence ?: 0.9f
                                )
                            )
                        }
                    }
                }

                onCandidatesDetected(candidates)
            }
            .addOnFailureListener {
                // Fail visibly without crashing
                onCandidatesDetected(emptyList())
            }
            .addOnCompleteListener {
                isBusy.set(false)
                imageProxy.close()
            }
    }

    private fun normalizeBoundingBox(box: Rect, imageWidth: Int, imageHeight: Int): RectF {
        val w = imageWidth.toFloat().coerceAtLeast(1f)
        val h = imageHeight.toFloat().coerceAtLeast(1f)

        return RectF(
            (box.left / w).coerceIn(0f, 1f),
            (box.top / h).coerceIn(0f, 1f),
            (box.width() / w).coerceIn(0.01f, 1f),
            (box.height() / h).coerceIn(0.01f, 1f)
        )
    }
}
