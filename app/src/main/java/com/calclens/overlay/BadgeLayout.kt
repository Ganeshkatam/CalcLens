package com.calclens.overlay

import com.calclens.tracking.TrackingStatus
import com.calclens.vision.RectBounds

data class BadgeLayout(
    val id: String,
    val badgeRect: RectBounds,
    val expressionRect: RectBounds,
    val result: String?,
    val errorMessage: String?,
    val status: TrackingStatus,
    val rawText: String
)
