package com.calclens.overlay

import com.calclens.vision.RectBounds
import kotlin.math.max
import kotlin.math.min

class CoordinateTransformer(
    var viewportWidth: Float,
    var viewportHeight: Float,
    var sensorWidth: Float = 1080f,
    var sensorHeight: Float = 1920f
) {
    fun toScreenRect(normalizedBox: RectBounds): RectBounds {
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

        return RectBounds(left, top, right, bottom)
    }

    fun computeBadgePlacement(
        expressionRect: RectBounds,
        textLength: Int = 2,
        badgeHeight: Float = 56f,
        margin: Float = 14f
    ): RectBounds {
        // Dynamically size badge to comfortably fit the answer without encroaching on adjacent columns
        val charWidth = 22f
        val horizontalPadding = 48f
        val desiredWidth = textLength * charWidth + horizontalPadding
        val maxAllowedWidth = max(expressionRect.width * 1.20f, 160f)
        val badgeWidth = desiredWidth.coerceIn(90f, maxAllowedWidth)

        // Anchor strictly to expression's horizontal center and bottom baseline
        var top = expressionRect.bottom + margin
        var left = expressionRect.centerX - badgeWidth / 2f

        // Flip above if clipping viewport bottom
        if (top + badgeHeight > viewportHeight - 60f) {
            top = max(40f, expressionRect.top - badgeHeight - margin)
        }

        // Clamp horizontally to screen viewport
        left = max(16f, min(viewportWidth - badgeWidth - 16f, left))

        return RectBounds(left, top, left + badgeWidth, top + badgeHeight)
    }
}
