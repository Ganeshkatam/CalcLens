package com.calclens.overlay

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class CoordinateTransformer(
    var viewportWidth: Float,
    var viewportHeight: Float,
    var sensorWidth: Float = 1080f,
    var sensorHeight: Float = 1920f
) {
    fun toScreenRect(normalizedBox: RectF): RectF {
        val scale = max(
            viewportWidth / sensorWidth,
            viewportHeight / sensorHeight
        )

        val scaledWidth = sensorWidth * scale
        val scaledHeight = sensorHeight * scale

        val offsetX = (scaledWidth - viewportWidth) / 2f
        val offsetY = (scaledHeight - viewportHeight) / 2f

        val left = normalizedBox.left * scaledWidth - offsetX
        val top = normalizedBox.top * scaledHeight - offsetY
        val right = normalizedBox.right * scaledWidth - offsetX
        val bottom = normalizedBox.bottom * scaledHeight - offsetY

        return RectF(left, top, right, bottom)
    }

    fun computeBadgePlacement(
        expressionRect: RectF,
        badgeWidth: Float = 220f,
        badgeHeight: Float = 90f,
        margin: Float = 20f
    ): RectF {
        var top = expressionRect.bottom + margin
        var left = expressionRect.left + (expressionRect.width() - badgeWidth) / 2f

        // Flip above if clipping viewport bottom
        if (top + badgeHeight > viewportHeight - 60f) {
            top = max(60f, expressionRect.top - badgeHeight - margin)
        }

        // Clamp horizontally
        left = max(24f, min(viewportWidth - badgeWidth - 24f, left))

        return RectF(left, top, left + badgeWidth, top + badgeHeight)
    }
}
