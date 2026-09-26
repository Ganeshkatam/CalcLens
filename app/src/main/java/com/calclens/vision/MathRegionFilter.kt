package com.calclens.vision

object MathRegionFilter {
    private val mathPattern = Regex("^[0-9().\\s]+[+\\-*/][0-9().\\s+\\-*/]*$")

    fun isViableArithmetic(
        expression: String,
        confidence: Float = 1.0f,
        minConfidence: Float = 0.60f
    ): Boolean {
        if (confidence < minConfidence) return false

        val trimmed = expression.trim()
        if (trimmed.length < 3) return false

        // Must contain at least one operator
        if (!trimmed.contains(Regex("[+\\-*/]"))) return false

        // Must contain at least two digits
        val digitCount = trimmed.count { it.isDigit() }
        if (digitCount < 2) return false

        return mathPattern.matches(trimmed)
    }
}
