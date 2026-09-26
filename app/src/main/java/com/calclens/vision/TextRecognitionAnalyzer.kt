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
                val rawLines = mutableListOf<RawTextLine>()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val rawText = line.text
                        val normalized = ExpressionNormalizer.normalize(rawText)
                        val box = line.boundingBox
                        val normalizedBox = if (box != null) {
                            normalizeBoundingBox(box, imageWidth, imageHeight)
                        } else {
                            RectBounds(0.2f, 0.4f, 0.8f, 0.5f)
                        }

                        android.util.Log.d("CalcLens", "OCR Line: '$rawText' -> Normalized: '$normalized' (confidence=${line.confidence})")

                        rawLines.add(
                            RawTextLine(
                                rawText = rawText,
                                normalizedText = normalized,
                                bounds = normalizedBox,
                                confidence = line.confidence
                            )
                        )
                    }
                }

                val candidates = SpatialExpressionReconstructor.reconstruct(rawLines) {
                    "cand-${idCounter.incrementAndGet()}"
                }

                for (candidate in candidates) {
                    android.util.Log.d("CalcLens", "Candidate Accepted: '${candidate.normalizedText}' at ${candidate.boundingBox}")
                }

                if (candidates.isNotEmpty()) {
                    android.util.Log.d("CalcLens", "Total viable candidates in frame: ${candidates.size}")
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

    private fun normalizeBoundingBox(box: Rect, imageWidth: Int, imageHeight: Int): RectBounds {
        val w = imageWidth.toFloat().coerceAtLeast(1f)
        val h = imageHeight.toFloat().coerceAtLeast(1f)

        return RectBounds(
            (box.left.toFloat() / w).coerceIn(0f, 1f),
            (box.top.toFloat() / h).coerceIn(0f, 1f),
            (box.right.toFloat() / w).coerceIn(0f, 1f),
            (box.bottom.toFloat() / h).coerceIn(0f, 1f)
        )
    }
}
