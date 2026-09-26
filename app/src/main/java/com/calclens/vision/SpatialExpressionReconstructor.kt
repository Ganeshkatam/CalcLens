package com.calclens.vision

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RectBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

data class RawTextLine(
    val rawText: String,
    val normalizedText: String,
    val bounds: RectBounds,
    val confidence: Float
)

object SpatialExpressionReconstructor {

    // Matches leading problem labels like "1. ", "11. ", "23) ", "(5) "
    private val problemNumberRegex = Regex("^\\s*\\(?\\d{1,3}[.)]\\s+")

    fun stripProblemNumber(text: String): String {
        return text.replace(problemNumberRegex, "").trim()
    }

    fun isSeparatorLine(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && trimmed.all { it in "-=_—–" }
    }

    fun isPureNumber(text: String): Boolean {
        val cleaned = stripProblemNumber(text).trim()
        if (cleaned.isEmpty()) return false
        // Allow integer or decimal number (e.g. "8", "125", "3.75")
        return cleaned.toDoubleOrNull() != null
    }

    fun extractOperatorAndNumber(text: String): Pair<Char, String>? {
        val cleaned = stripProblemNumber(text).trim()
        if (cleaned.length < 2) return null
        val firstChar = cleaned.first()
        if (firstChar !in "+-*/") return null
        val remainder = cleaned.substring(1).trim()
        if (remainder.isEmpty() || remainder.toDoubleOrNull() == null) return null
        return Pair(firstChar, remainder)
    }

    fun reconstruct(
        lines: List<RawTextLine>,
        minConfidence: Float = 0.60f,
        idGenerator: () -> String
    ): List<VisionCandidate> {
        val candidates = mutableListOf<VisionCandidate>()
        val consumedLineIndices = mutableSetOf<Int>()

        // 1. First pass: Identify complete horizontal single-line expressions
        for (i in lines.indices) {
            val line = lines[i]
            if (isSeparatorLine(line.normalizedText)) {
                consumedLineIndices.add(i)
                continue
            }

            val stripped = stripProblemNumber(line.normalizedText)
            if (MathRegionFilter.isViableArithmetic(stripped, confidence = line.confidence, minConfidence = minConfidence)) {
                candidates.add(
                    VisionCandidate(
                        id = idGenerator(),
                        rawText = line.rawText,
                        normalizedText = stripped,
                        boundingBox = line.bounds.toRectF(),
                        confidence = line.confidence
                    )
                )
                consumedLineIndices.add(i)
            }
        }

        // 2. Second pass: Vertical / Column arithmetic reconstruction
        // Look for bottom lines with operator + number (e.g. "+ 7", "+ 87", "- 25", "* 27", "+ 8.6")
        for (bIdx in lines.indices) {
            if (consumedLineIndices.contains(bIdx)) continue

            val bottomLine = lines[bIdx]
            val opAndNum = extractOperatorAndNumber(bottomLine.normalizedText) ?: continue
            val (operator, bottomNumber) = opAndNum

            val bottomBox = bottomLine.bounds
            val bottomHeight = max(0.01f, bottomBox.height)
            val bottomWidth = max(0.01f, bottomBox.width)

            var bestTopIdx: Int? = null
            var bestDistance = Float.MAX_VALUE

            for (tIdx in lines.indices) {
                if (tIdx == bIdx || consumedLineIndices.contains(tIdx)) continue

                val topLine = lines[tIdx]
                if (!isPureNumber(topLine.normalizedText)) continue

                val topBox = topLine.bounds

                // Must be above the bottom line
                if (topBox.top >= bottomBox.top) continue

                val verticalGap = bottomBox.top - topBox.bottom
                // Vertical gap check: accounts for spacing and optional separator line
                if (verticalGap < -0.35f * bottomHeight || verticalGap > 3.0f * bottomHeight) continue

                val topWidth = max(0.01f, topBox.width)

                // Horizontal alignment check
                val overlapLeft = max(topBox.left, bottomBox.left)
                val overlapRight = min(topBox.right, bottomBox.right)
                val overlapWidth = max(0f, overlapRight - overlapLeft)
                val minWidth = min(topWidth, bottomWidth)

                val centerDistX = abs(topBox.centerX - bottomBox.centerX)
                val isAligned = (overlapWidth >= 0.20f * minWidth) || (centerDistX <= max(topWidth, bottomWidth) * 0.85f)

                if (isAligned && verticalGap < bestDistance) {
                    bestDistance = verticalGap
                    bestTopIdx = tIdx
                }
            }

            if (bestTopIdx != null) {
                val topLine = lines[bestTopIdx]
                val topNumber = stripProblemNumber(topLine.normalizedText).trim()
                val combinedText = "$topNumber $operator $bottomNumber"
                val combinedConfidence = min(topLine.confidence, bottomLine.confidence)

                if (combinedConfidence >= minConfidence) {
                    val unionBox = RectF(
                        min(topLine.bounds.left, bottomLine.bounds.left),
                        topLine.bounds.top,
                        max(topLine.bounds.right, bottomLine.bounds.right),
                        bottomLine.bounds.bottom
                    )

                    candidates.add(
                        VisionCandidate(
                            id = idGenerator(),
                            rawText = "${topLine.rawText}\n${bottomLine.rawText}",
                            normalizedText = combinedText,
                            boundingBox = unionBox,
                            confidence = combinedConfidence
                        )
                    )

                    consumedLineIndices.add(bIdx)
                    consumedLineIndices.add(bestTopIdx)
                }
            }
        }

        return candidates
    }
}
