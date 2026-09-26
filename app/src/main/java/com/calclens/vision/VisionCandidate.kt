package com.calclens.vision

import android.graphics.RectF

data class VisionCandidate(
    val id: String,
    val rawText: String,
    val normalizedText: String,
    val boundingBox: RectF, // Normalized coordinates [0.0f, 1.0f]
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)
