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
        margin: Float = 14f
    ): RectBounds {
        // Dynamically scale badge height based on expression scale on screen
        val scaledHeight = (expressionRect.height * 0.40f).coerceIn(40f, 58f)
        val charWidth = scaledHeight * 0.42f
        val horizontalPadding = scaledHeight * 0.90f
        val desiredWidth = textLength * charWidth + horizontalPadding
        val maxAllowedWidth = max(expressionRect.width * 1.25f, 160f)
        val badgeWidth = desiredWidth.coerceIn(80f, maxAllowedWidth)

        // Anchor strictly to expression's horizontal center and bottom baseline
        var top = expressionRect.bottom + margin
        var left = expressionRect.centerX - badgeWidth / 2f

        // Flip above if clipping viewport bottom
        if (top + scaledHeight > viewportHeight - 60f) {
            top = max(40f, expressionRect.top - scaledHeight - margin)
        }

        // Clamp horizontally to screen viewport
        left = max(16f, min(viewportWidth - badgeWidth - 16f, left))

        return RectBounds(left, top, left + badgeWidth, top + scaledHeight)
    }
}
