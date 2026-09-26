package com.calclens.tracking

import com.calclens.vision.RectBounds

enum class TrackingStatus {
    DETECTED,
    DISPLAYING,
    TRACKING,
    LOST
}

data class TrackedEquation(
    val id: String,
    var rawText: String,
    var normalizedText: String,
    var result: String? = null,
    var errorMessage: String? = null,
    var confidence: Float,
    var boundingBox: RectBounds,
    var smoothedBox: RectBounds,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    val firstSeen: Long = System.currentTimeMillis(),
    var lastSeen: Long = System.currentTimeMillis(),
    var consecutiveMatches: Int = 1,
    var status: TrackingStatus = TrackingStatus.DETECTED
)
