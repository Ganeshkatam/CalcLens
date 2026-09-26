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
    val confidence: Float,
    val pixelHeight: Float = 0f
)

data class ReconstructionResult(
    val candidates: List<VisionCandidate>,
    val hasTooFarText: Boolean = false
)

private data class ColumnRow(
    val operator: Char?,
    val number: String?,
    val bounds: RectBounds,
    val confidence: Float,
    val sourceLineIndices: List<Int>,
    val numberBounds: RectBounds = bounds
)


private data class ColumnCluster(
    val rows: MutableList<ColumnRow>,
    var bounds: RectBounds
) {
    val centerX: Float get() = bounds.centerX
    val averageWidth: Float get() = bounds.width
}

object SpatialExpressionReconstructor {

    // Thresholds for character resolution operating envelope
    const val MIN_LINE_PIXEL_HEIGHT = 10f
    const val MIN_LINE_NORMALIZED_HEIGHT = 0.008f

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

    fun cleanHorizontalExpression(text: String): String {
        val stripped = stripProblemNumber(text)
        return stripped.trimEnd('=', ' ').trim()
    }

    fun isSeparatorLine(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && trimmed.all { it in "-=_—–─━―~" }
    }

    fun isPureNumber(text: String): Boolean {
        val cleaned = stripProblemNumber(text).trim()
        if (cleaned.isEmpty()) return false
        return cleaned.toDoubleOrNull() != null
    }

    fun canonicalizeOperator(c: Char): Char? {
        return when (c) {
            '+', '＋' -> '+'
            '-', '−', '–', '—' -> '-'
            '*', 'x', 'X', '×', '•' -> '*'
            '/', '÷', ':' -> '/'
            else -> null
        }
    }

    fun isSingleOperator(text: String): Boolean {
        val trimmed = stripProblemNumber(text).trim()
        if (trimmed.length != 1) return false
        return canonicalizeOperator(trimmed.first()) != null
    }

    fun extractSingleOperator(text: String): Char? {
        val trimmed = stripProblemNumber(text).trim()
        if (trimmed.length != 1) return null
        return canonicalizeOperator(trimmed.first())
    }

    fun extractOperatorAndNumber(text: String): Pair<Char, String>? {
        val cleaned = stripProblemNumber(text).trim()
        if (cleaned.length < 2) return null
        val firstChar = canonicalizeOperator(cleaned.first()) ?: return null
        val remainder = cleaned.substring(1).trim()
        if (remainder.isEmpty() || remainder.toDoubleOrNull() == null) return null
        return Pair(firstChar, remainder)
    }

    fun isLineTooSmall(line: RawTextLine): Boolean {
        // Operators and separator lines are naturally thin and must never be dropped by size
        if (isSingleOperator(line.normalizedText) || isSingleOperator(line.rawText)) return false
        if (isSeparatorLine(line.normalizedText) || isSeparatorLine(line.rawText)) return false

        if (line.pixelHeight > 0f && line.pixelHeight < MIN_LINE_PIXEL_HEIGHT) return true
        if (line.pixelHeight <= 0f && line.bounds.height < MIN_LINE_NORMALIZED_HEIGHT) return true
        return false
    }

    fun isClippedByScreenEdge(bounds: RectBounds): Boolean {
        return bounds.left <= 0.005f || bounds.top <= 0.005f || bounds.right >= 0.995f || bounds.bottom >= 0.995f
    }

    fun reconstruct(
        lines: List<RawTextLine>,
        minConfidence: Float = 0.60f,
        idGenerator: () -> String
    ): List<VisionCandidate> {
        return reconstructWithDiagnostics(lines, minConfidence, idGenerator).candidates
    }

    fun reconstructWithDiagnostics(
        lines: List<RawTextLine>,
        minConfidence: Float = 0.60f,
        idGenerator: () -> String
    ): ReconstructionResult {
        val candidates = mutableListOf<VisionCandidate>()
        val consumedLineIndices = mutableSetOf<Int>()
        var hasTooFarText = false

        // Check if any arithmetic-like text is present but too small to be read reliably
        for (line in lines) {
            val hasDigits = line.rawText.any { it.isDigit() }
            if (hasDigits && isLineTooSmall(line)) {
                hasTooFarText = true
            }
        }

        // 1. First pass: Identify complete horizontal single-line expressions
        for (i in lines.indices) {
            val line = lines[i]
            if (isSeparatorLine(line.normalizedText) || isSeparatorLine(line.rawText)) {
                continue
            }
            if (isProblemLabel(line.rawText) || isProblemLabel(line.normalizedText)) {
                continue
            }
            if (isLineTooSmall(line) || isClippedByScreenEdge(line.bounds)) {
                continue
            }

            val stripped = cleanHorizontalExpression(line.normalizedText)
            if (MathRegionFilter.isViableArithmetic(stripped, confidence = line.confidence, minConfidence = minConfidence)) {
                candidates.add(
                    VisionCandidate(
                        id = idGenerator(),
                        rawText = line.rawText,
                        normalizedText = stripped,
                        boundingBox = line.bounds,
                        confidence = line.confidence,
                        layout = ExpressionLayout.HORIZONTAL,
                        contentBounds = line.bounds
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
            if (isLineTooSmall(opLine)) continue

            val opChar = extractSingleOperator(opLine.normalizedText)
                ?: extractSingleOperator(opLine.rawText)
                ?: continue

            var bestNumIdx: Int? = null
            var bestHGap = Float.MAX_VALUE
            for (numIdx in lines.indices) {
                if (numIdx == opIdx || consumedLineIndices.contains(numIdx) || usedInRows.contains(numIdx)) continue
                val numLine = lines[numIdx]
                if (isProblemLabel(numLine.rawText) || isSeparatorLine(numLine.rawText)) continue
                if (isLineTooSmall(numLine)) continue
                if (!isPureNumber(numLine.normalizedText)) continue

                val vCenterDiff = abs(numLine.bounds.centerY - opLine.bounds.centerY)
                val maxHeight = max(opLine.bounds.height, numLine.bounds.height)
                if (vCenterDiff <= maxHeight * 0.75f) {
                    val hGap = numLine.bounds.left - opLine.bounds.right
                    if (hGap >= -0.05f && hGap <= 0.35f && hGap < bestHGap) {
                        bestHGap = hGap
                        bestNumIdx = numIdx
                    }
                }
            }

            if (bestNumIdx != null) {
                val numLine = lines[bestNumIdx]
                val numStr = stripProblemNumber(numLine.normalizedText).trim()
                val mergedBox = RectBounds(
                    min(opLine.bounds.left, numLine.bounds.left),
                    min(opLine.bounds.top, numLine.bounds.top),
                    max(opLine.bounds.right, numLine.bounds.right),
                    max(opLine.bounds.bottom, numLine.bounds.bottom)
                )
                columnRows.add(
                    ColumnRow(
                        operator = opChar,
                        number = numStr,
                        bounds = mergedBox,
                        confidence = numLine.confidence,
                        sourceLineIndices = listOf(opIdx, bestNumIdx),
                        numberBounds = numLine.bounds
                    )
                )
                usedInRows.add(opIdx)
                usedInRows.add(bestNumIdx)
            }
        }


        // 2b. Extract combined operator + number lines (e.g. "+ 15", "- 27", "× 2", "÷ 3")
        for (idx in lines.indices) {
            if (consumedLineIndices.contains(idx) || usedInRows.contains(idx)) continue
            val line = lines[idx]
            if (isProblemLabel(line.rawText) || isSeparatorLine(line.rawText)) continue
            if (isLineTooSmall(line)) continue

            val opAndNum = extractOperatorAndNumber(line.normalizedText)
                ?: extractOperatorAndNumber(line.rawText)
            if (opAndNum != null) {
                val numWidth = line.bounds.width * 0.65f
                val numBounds = RectBounds(
                    line.bounds.right - numWidth,
                    line.bounds.top,
                    line.bounds.right,
                    line.bounds.bottom
                )
                columnRows.add(
                    ColumnRow(
                        operator = opAndNum.first,
                        number = opAndNum.second,
                        bounds = line.bounds,
                        confidence = line.confidence,
                        sourceLineIndices = listOf(idx),
                        numberBounds = numBounds
                    )
                )
                usedInRows.add(idx)
            }
        }

        // 2c. Extract pure numbers (e.g. "5", "12", "65", "125")
        for (idx in lines.indices) {
            if (consumedLineIndices.contains(idx) || usedInRows.contains(idx)) continue
            val line = lines[idx]
            if (isProblemLabel(line.rawText) || isSeparatorLine(line.rawText)) continue
            if (isLineTooSmall(line)) continue

            if (isPureNumber(line.normalizedText)) {
                val numStr = stripProblemNumber(line.normalizedText).trim()
                columnRows.add(
                    ColumnRow(
                        operator = null,
                        number = numStr,
                        bounds = line.bounds,
                        confidence = line.confidence,
                        sourceLineIndices = listOf(idx),
                        numberBounds = line.bounds
                    )
                )
                usedInRows.add(idx)
            }
        }

        // 2d. Extract standalone operator lines not horizontally merged in 2a (e.g. "×", "÷", "-", "+" between rows)
        for (idx in lines.indices) {
            if (consumedLineIndices.contains(idx) || usedInRows.contains(idx)) continue
            val line = lines[idx]
            if (isProblemLabel(line.rawText) || isSeparatorLine(line.rawText)) continue
            if (isLineTooSmall(line)) continue

            val opChar = extractSingleOperator(line.normalizedText)
                ?: extractSingleOperator(line.rawText)
            if (opChar != null) {
                columnRows.add(
                    ColumnRow(
                        operator = opChar,
                        number = null,
                        bounds = line.bounds,
                        confidence = line.confidence,
                        sourceLineIndices = listOf(idx),
                        numberBounds = line.bounds
                    )
                )
                usedInRows.add(idx)
            }
        }

        // 2e. Sort all candidate rows by their vertical top position
        columnRows.sortBy { it.bounds.top }

        // Identify all separator lines in the frame to serve as structural problem boundaries
        val separatorLines = lines.filter { isSeparatorLine(it.rawText) || isSeparatorLine(it.normalizedText) }

        // 2f. Cluster column rows into distinct column problems
        val clusters = mutableListOf<ColumnCluster>()
        for (row in columnRows) {
            var matchedCluster: ColumnCluster? = null
            var bestDistance = Float.MAX_VALUE

            for (cluster in clusters) {
                val lastRow = cluster.rows.last()

                if (row.bounds.top < lastRow.bounds.top) continue

                // Barrier 1: In elementary arithmetic, you never have an operand without an operator following an existing completed binary problem
                val numbersInCluster = cluster.rows.count { it.number != null }
                if (numbersInCluster >= 2 && row.number != null && row.operator == null) {
                    val prevIsOperator = lastRow.number == null && lastRow.operator != null
                    if (!prevIsOperator) continue
                }

                // Barrier 2: Hard structural barrier: a horizontal separator line between lastRow and row indicates a separate problem
                val hasSeparatorBetween = separatorLines.any { sep ->
                    val vBetween = sep.bounds.centerY in lastRow.bounds.bottom..row.bounds.top
                    val hOverlap = max(0f, min(sep.bounds.right, cluster.bounds.right) - max(sep.bounds.left, cluster.bounds.left))
                    vBetween && hOverlap >= 0.15f * min(sep.bounds.width, cluster.averageWidth)
                }
                if (hasSeparatorBetween) continue

                val vGap = row.bounds.top - lastRow.bounds.bottom
                val maxHeight = max(lastRow.bounds.height, row.bounds.height)
                if (vGap < -0.35f * maxHeight || vGap > 1.8f * maxHeight) continue

                val overlapLeft = max(row.bounds.left, cluster.bounds.left)
                val overlapRight = min(row.bounds.right, cluster.bounds.right)
                val overlapWidth = max(0f, overlapRight - overlapLeft)
                val minW = min(row.bounds.width, cluster.bounds.width)

                val centerDiffX = abs(row.bounds.centerX - cluster.centerX)
                val isAligned = (overlapWidth >= 0.15f * minW) ||
                        (centerDiffX <= max(row.bounds.width, cluster.averageWidth) * 0.90f) ||
                        (row.operator != null && row.bounds.right >= cluster.bounds.left - 0.06f && row.bounds.left <= cluster.bounds.right)

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

        // 2g. Process each cluster into a full mathematical candidate
        for (cluster in clusters) {
            val operandRows = cluster.rows.filter { it.number != null }
            if (operandRows.size < 2) continue

            val explicitOps = cluster.rows.mapNotNull { it.operator }
            if (explicitOps.isEmpty()) continue

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

            val topNumber = operandRows.first().number!!
            val sb = StringBuilder(topNumber)
            val fallbackOp = explicitOps.first()

            for (rIdx in 1 until operandRows.size) {
                val opRow = operandRows[rIdx]
                val explicitOpOnRow = opRow.operator
                val interveningOp = if (explicitOpOnRow == null) {
                    val prevRow = operandRows[rIdx - 1]
                    cluster.rows.find {
                        it.number == null && it.operator != null &&
                        it.bounds.centerY in prevRow.bounds.centerY..opRow.bounds.centerY
                    }?.operator
                } else null

                val op = explicitOpOnRow ?: interveningOp ?: fallbackOp
                sb.append(" ").append(op).append(" ").append(opRow.number)
            }

            val expressionText = sb.toString()
            val clusterConfidence = cluster.rows.map { it.confidence }.average().toFloat()

            if (clusterConfidence < minConfidence) continue
            if (!MathRegionFilter.isViableArithmetic(expressionText, confidence = clusterConfidence, minConfidence = minConfidence)) continue

            if (MathEngine.evaluate(expressionText) is MathResult.SyntaxError) continue

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

            // Reject expressions cropped by the sensor image boundary to prevent incomplete arithmetic
            if (isClippedByScreenEdge(unionBox)) continue

            val rawText = cluster.rows.joinToString("\n") { row ->
                if (row.operator != null && row.number != null) "${row.operator} ${row.number}"
                else if (row.operator != null) "${row.operator}"
                else row.number ?: ""
            }

            // Compute digits column bounds across all operand rows in cluster (excludes leftmost operator)
            val digitLeft = operandRows.map { it.numberBounds.left }.minOrNull() ?: unionBox.left
            val digitTop = operandRows.map { it.numberBounds.top }.minOrNull() ?: unionBox.top
            val digitRight = operandRows.map { it.numberBounds.right }.maxOrNull() ?: unionBox.right
            val digitBottom = operandRows.map { it.numberBounds.bottom }.maxOrNull() ?: unionBox.bottom
            val digitsBounds = RectBounds(digitLeft, digitTop, digitRight, digitBottom)

            candidates.add(
                VisionCandidate(
                    id = idGenerator(),
                    rawText = rawText,
                    normalizedText = expressionText,
                    boundingBox = unionBox,
                    confidence = clusterConfidence,
                    layout = ExpressionLayout.VERTICAL_COLUMN,
                    contentBounds = digitsBounds
                )
            )

            for (row in cluster.rows) {
                consumedLineIndices.addAll(row.sourceLineIndices)
            }
        }

        return ReconstructionResult(candidates, hasTooFarText)
    }
}
