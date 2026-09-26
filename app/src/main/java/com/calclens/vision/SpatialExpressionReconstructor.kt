package com.calclens.vision

import android.graphics.RectF
import com.calclens.math.MathEngine
import com.calclens.math.MathResult
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

    fun offset(dx: Float, dy: Float): RectBounds = RectBounds(left + dx, top + dy, right + dx, bottom + dy)
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

data class RawTextLine(
    val rawText: String,
    val normalizedText: String,
    val bounds: RectBounds,
    val confidence: Float
)

private data class ColumnRow(
    val operator: Char?,
    val number: String,
    val bounds: RectBounds,
    val confidence: Float,
    val sourceLineIndices: List<Int>
)

private data class ColumnCluster(
    val rows: MutableList<ColumnRow>,
    var bounds: RectBounds
) {
    val centerX: Float get() = bounds.centerX
    val averageWidth: Float get() = bounds.width
}

object SpatialExpressionReconstructor {

    // Matches standalone problem labels like "(i)", "(iv)", "(a)", "1.", "1)", "(12)", "[3]"
    private val standaloneProblemLabelRegex = Regex(
        """^\s*(?:\([a-zA-Z0-9ivxLCDM]+\)|\[[a-zA-Z0-9ivxLCDM]+\]|[a-zA-Z0-9ivxLCDM]{1,4}[.)/:])\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Matches leading problem labels like "(i) ", "1. ", "23) ", "(5) ", "8/ ", "1: "
    // Preserves decimals like "3.75" because periods must be followed by whitespace or parenthesis/colon.
    private val problemNumberRegex = Regex(
        """^\s*(?:\([a-zA-Z0-9ivxLCDM]+\)\s*|\[[a-zA-Z0-9ivxLCDM]+\]\s*|[a-zA-Z0-9ivxLCDM]{1,4}[.)/:]\s+)""",
        RegexOption.IGNORE_CASE
    )

    fun isProblemLabel(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return standaloneProblemLabelRegex.matches(trimmed)
    }

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
        return cleaned.toDoubleOrNull() != null
    }

    fun isSingleOperator(text: String): Boolean {
        val trimmed = stripProblemNumber(text).trim()
        return trimmed.length == 1 && trimmed.first() in "+-*/"
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
            if (isSeparatorLine(line.normalizedText) || isSeparatorLine(line.rawText)) {
                continue
            }
            if (isProblemLabel(line.rawText) || isProblemLabel(line.normalizedText)) {
                continue
            }

            val stripped = stripProblemNumber(line.normalizedText)
            if (MathRegionFilter.isViableArithmetic(stripped, confidence = line.confidence, minConfidence = minConfidence)) {
                candidates.add(
                    VisionCandidate(
                        id = idGenerator(),
                        rawText = line.rawText,
                        normalizedText = stripped,
                        boundingBox = line.bounds,
                        confidence = line.confidence
                    )
                )
                consumedLineIndices.add(i)
            }
        }

        // 2. Second pass: Vertical / Column arithmetic clustering & reconstruction
        val columnRows = mutableListOf<ColumnRow>()
        val usedInRows = mutableSetOf<Int>()

        // 2a. Detect split operator and number on the same horizontal row (e.g. "+" on its own line next to "15")
        for (opIdx in lines.indices) {
            if (consumedLineIndices.contains(opIdx) || usedInRows.contains(opIdx)) continue
            val opLine = lines[opIdx]
            if (isProblemLabel(opLine.rawText) || isSeparatorLine(opLine.rawText)) continue

            val opChar = when {
                isSingleOperator(opLine.normalizedText) -> opLine.normalizedText.trim().first()
                isSingleOperator(opLine.rawText) -> opLine.rawText.trim().first()
                else -> null
            } ?: continue

            var bestNumIdx: Int? = null
            var bestHGap = Float.MAX_VALUE
            for (numIdx in lines.indices) {
                if (numIdx == opIdx || consumedLineIndices.contains(numIdx) || usedInRows.contains(numIdx)) continue
                val numLine = lines[numIdx]
                if (isProblemLabel(numLine.rawText) || isSeparatorLine(numLine.rawText)) continue
                if (!isPureNumber(numLine.normalizedText)) continue

                val vCenterDiff = abs(numLine.bounds.centerY - opLine.bounds.centerY)
                if (vCenterDiff <= max(opLine.bounds.height, numLine.bounds.height) * 0.75f) {
                    val hGap = numLine.bounds.left - opLine.bounds.right
                    if (hGap >= -0.05f && hGap <= 0.25f && hGap < bestHGap) {
                        bestHGap = hGap
                        bestNumIdx = numIdx
                    }
                }
            }

            if (bestNumIdx != null) {
                val numLine = lines[bestNumIdx]
                val numStr = stripProblemNumber(numLine.normalizedText).trim()
                val mergedBox = RectBounds(
                    opLine.bounds.left,
                    min(opLine.bounds.top, numLine.bounds.top),
                    numLine.bounds.right,
                    max(opLine.bounds.bottom, numLine.bounds.bottom)
                )
                columnRows.add(
                    ColumnRow(
                        operator = opChar,
                        number = numStr,
                        bounds = mergedBox,
                        confidence = min(opLine.confidence, numLine.confidence),
                        sourceLineIndices = listOf(opIdx, bestNumIdx)
                    )
                )
                usedInRows.add(opIdx)
                usedInRows.add(bestNumIdx)
            }
        }

        // 2b. Extract combined operator + number lines (e.g. "+ 15", "- 27")
        for (idx in lines.indices) {
            if (consumedLineIndices.contains(idx) || usedInRows.contains(idx)) continue
            val line = lines[idx]
            if (isProblemLabel(line.rawText) || isSeparatorLine(line.rawText)) continue

            val opAndNum = extractOperatorAndNumber(line.normalizedText)
                ?: extractOperatorAndNumber(line.rawText)
            if (opAndNum != null) {
                columnRows.add(
                    ColumnRow(
                        operator = opAndNum.first,
                        number = opAndNum.second,
                        bounds = line.bounds,
                        confidence = line.confidence,
                        sourceLineIndices = listOf(idx)
                    )
                )
                usedInRows.add(idx)
            }
        }

        // 2c. Extract pure numbers (e.g. "12", "65", "125")
        for (idx in lines.indices) {
            if (consumedLineIndices.contains(idx) || usedInRows.contains(idx)) continue
            val line = lines[idx]
            if (isProblemLabel(line.rawText) || isSeparatorLine(line.rawText)) continue

            if (isPureNumber(line.normalizedText)) {
                val numStr = stripProblemNumber(line.normalizedText).trim()
                columnRows.add(
                    ColumnRow(
                        operator = null,
                        number = numStr,
                        bounds = line.bounds,
                        confidence = line.confidence,
                        sourceLineIndices = listOf(idx)
                    )
                )
                usedInRows.add(idx)
            }
        }

        // 2d. Sort all candidate rows by their vertical top position
        columnRows.sortBy { it.bounds.top }

        // 2e. Cluster column rows into distinct column problems
        val clusters = mutableListOf<ColumnCluster>()
        for (row in columnRows) {
            var matchedCluster: ColumnCluster? = null
            var bestDistance = Float.MAX_VALUE

            for (cluster in clusters) {
                val lastRow = cluster.rows.last()

                // Must be below the last row in the cluster
                if (row.bounds.top < lastRow.bounds.top) continue

                val vGap = row.bounds.top - lastRow.bounds.bottom
                val maxHeight = max(lastRow.bounds.height, row.bounds.height)
                // Adjacent vertical spacing: within standard line height spacing (-0.35 to 1.6x line height)
                if (vGap < -0.35f * maxHeight || vGap > 1.6f * maxHeight) continue

                // Horizontal alignment check
                val overlapLeft = max(row.bounds.left, cluster.bounds.left)
                val overlapRight = min(row.bounds.right, cluster.bounds.right)
                val overlapWidth = max(0f, overlapRight - overlapLeft)
                val minW = min(row.bounds.width, cluster.bounds.width)

                val centerDiffX = abs(row.bounds.centerX - cluster.centerX)
                val isAligned = (overlapWidth >= 0.20f * minW) ||
                        (centerDiffX <= max(row.bounds.width, cluster.averageWidth) * 0.85f)

                if (isAligned && vGap < bestDistance) {
                    bestDistance = vGap
                    matchedCluster = cluster
                }
            }

            if (matchedCluster != null) {
                matchedCluster.rows.add(row)
                matchedCluster.bounds = RectBounds(
                    min(matchedCluster.bounds.left, row.bounds.left),
                    min(matchedCluster.bounds.top, row.bounds.top),
                    max(matchedCluster.bounds.right, row.bounds.right),
                    max(matchedCluster.bounds.bottom, row.bounds.bottom)
                )
            } else {
                clusters.add(
                    ColumnCluster(
                        rows = mutableListOf(row),
                        bounds = row.bounds
                    )
                )
            }
        }

        // 2f. Process each cluster into a full mathematical candidate
        for (cluster in clusters) {
            // Require at least 2 rows in column arithmetic (e.g. 12 and + 15, or 12, + 15, + 13)
            if (cluster.rows.size < 2) continue

            // Must contain at least one explicit arithmetic operator
            val explicitOps = cluster.rows.mapNotNull { it.operator }
            if (explicitOps.isEmpty()) continue

            // Check for printed separator line underneath the last row
            val lastRow = cluster.rows.last()
            val bottomBox = lastRow.bounds
            val bottomHeight = max(0.01f, bottomBox.height)
            val bottomWidth = max(0.01f, bottomBox.width)

            var absorbedSepIdx: Int? = null
            var sepBox: RectBounds? = null

            for (sIdx in lines.indices) {
                if (consumedLineIndices.contains(sIdx)) continue
                val sLine = lines[sIdx]
                if (isSeparatorLine(sLine.rawText) || isSeparatorLine(sLine.normalizedText)) {
                    val sB = sLine.bounds
                    val vGap = sB.top - bottomBox.bottom
                    if (vGap >= -0.30f * bottomHeight && vGap <= 1.3f * bottomHeight) {
                        val distCenter = abs(sB.centerX - bottomBox.centerX)
                        if (distCenter <= max(bottomWidth, sB.width) * 0.90f) {
                            absorbedSepIdx = sIdx
                            sepBox = sB
                            break
                        }
                    }
                }
            }

            // Construct expression string
            val topNumber = cluster.rows.first().number
            val sb = StringBuilder(topNumber)
            val fallbackOp = explicitOps.first()

            for (rIdx in 1 until cluster.rows.size) {
                val row = cluster.rows[rIdx]
                val op = row.operator ?: fallbackOp
                sb.append(" ").append(op).append(" ").append(row.number)
            }

            val expressionText = sb.toString()
            val clusterConfidence = cluster.rows.map { it.confidence }.minOrNull() ?: 0.5f

            if (clusterConfidence < minConfidence) continue
            if (!MathRegionFilter.isViableArithmetic(expressionText, confidence = clusterConfidence, minConfidence = minConfidence)) continue

            // Verify with math engine
            if (MathEngine.evaluate(expressionText) is MathResult.SyntaxError) continue

            // Compute unified spatial bounding box including separator line
            var boundLeft = cluster.bounds.left
            var boundTop = cluster.bounds.top
            var boundRight = cluster.bounds.right
            var boundBottom = cluster.bounds.bottom

            if (sepBox != null) {
                boundLeft = min(boundLeft, sepBox.left)
                boundRight = max(boundRight, sepBox.right)
                boundBottom = max(boundBottom, sepBox.bottom)
                absorbedSepIdx?.let { consumedLineIndices.add(it) }
            }

            val unionBox = RectBounds(boundLeft, boundTop, boundRight, boundBottom)
            val rawText = cluster.rows.joinToString("\n") { row ->
                if (row.operator != null) "${row.operator} ${row.number}" else row.number
            }

            candidates.add(
                VisionCandidate(
                    id = idGenerator(),
                    rawText = rawText,
                    normalizedText = expressionText,
                    boundingBox = unionBox,
                    confidence = clusterConfidence
                )
            )

            // Mark lines consumed
            for (row in cluster.rows) {
                consumedLineIndices.addAll(row.sourceLineIndices)
            }
        }

        return candidates
    }
}
