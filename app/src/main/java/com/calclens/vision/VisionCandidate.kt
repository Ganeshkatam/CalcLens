package com.calclens.vision

enum class ExpressionLayout {
    HORIZONTAL,
    VERTICAL_COLUMN
}

data class VisionCandidate(
    val id: String,
    val rawText: String,
    val normalizedText: String,
    val boundingBox: RectBounds, // Normalized coordinates [0.0f, 1.0f]
    val confidence: Float,
    val layout: ExpressionLayout = ExpressionLayout.HORIZONTAL,
    val contentBounds: RectBounds = boundingBox,
    val timestamp: Long = System.currentTimeMillis()
)
