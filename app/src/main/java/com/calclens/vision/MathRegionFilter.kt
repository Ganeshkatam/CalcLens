package com.calclens.vision

import com.calclens.math.MathEngine
import com.calclens.math.MathResult

object MathRegionFilter {
    // Allows optional leading sign or parenthesis, digits/decimals, operators, and closes with digit or paren
    private val mathPattern = Regex("^\\s*[-+]?\\s*[0-9(][0-9().\\s+\\-*/]*[0-9)]\\s*$")
    // Rejects invalid consecutive binary operator sequences (e.g. "-/", "+*", "/*", "++")
    private val invalidOperatorSequence = Regex("[+\\-*/]{2,}|[+\\-*/]\\s*[/^*]")

    fun isViableArithmetic(
        expression: String,
        confidence: Float = 1.0f,
        minConfidence: Float = 0.60f
    ): Boolean {
        if (confidence < minConfidence) return false

        val trimmed = expression.trim()
        if (trimmed.length < 3) return false

        // Must not contain invalid consecutive operator sequences
        if (invalidOperatorSequence.containsMatchIn(trimmed)) return false

        // Must contain at least two digits
        val digitCount = trimmed.count { it.isDigit() }
        if (digitCount < 2) return false

        // Must contain an active operation (binary operator or signed expression with parentheses)
        val hasOperation = if (trimmed.startsWith("-") || trimmed.startsWith("+")) {
            val afterSign = trimmed.substring(1).trim()
            afterSign.contains(Regex("[+\\-*/]")) || trimmed.contains("(")
        } else {
            trimmed.contains(Regex("[+\\-*/]"))
        }
        if (!hasOperation) return false

        if (!mathPattern.matches(trimmed)) return false

        // Deterministic grammar verification: reject if syntax is fundamentally broken
        return when (MathEngine.evaluate(trimmed)) {
            is MathResult.SyntaxError -> false
            else -> true
        }
    }
}
