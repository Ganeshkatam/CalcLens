package com.calclens.overlay

import android.graphics.RectF
import com.calclens.tracking.TrackingStatus

data class BadgeLayout(
    val id: String,
    var badgeRect: RectF,
    val expressionRect: RectF,
    val result: String?,
    val errorMessage: String?,
    val status: TrackingStatus,
    val rawText: String
)
