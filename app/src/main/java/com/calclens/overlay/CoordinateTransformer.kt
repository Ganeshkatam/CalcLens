package com.calclens.overlay

import com.calclens.vision.ExpressionLayout
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
        contentRect: RectBounds = expressionRect,
        layout: ExpressionLayout = ExpressionLayout.HORIZONTAL,
        textLength: Int = 2,
        margin: Float = 10f
    ): RectBounds {
        return when (layout) {
            ExpressionLayout.HORIZONTAL -> {
                // Dynamically scale badge height based on expression line height
                val scaledHeight = (expressionRect.height * 1.05f).coerceIn(36f, 52f)
                val charWidth = scaledHeight * 0.44f
                val horizontalPadding = scaledHeight * 0.70f
                val desiredWidth = textLength * charWidth + horizontalPadding
                val badgeWidth = desiredWidth.coerceIn(52f, 180f)

                // Natural reading flow: place immediately to the right of horizontal expressions
                val rightCandidateLeft = expressionRect.right + margin
                val canFitOnRight = (rightCandidateLeft + badgeWidth) <= (viewportWidth - 14f)

                if (canFitOnRight) {
                    val top = (expressionRect.centerY - scaledHeight / 2f).coerceIn(
                        14f,
                        viewportHeight - scaledHeight - 14f
                    )
                    RectBounds(rightCandidateLeft, top, rightCandidateLeft + badgeWidth, top + scaledHeight)
                } else {
                    // Fall back to placing below right-aligned with the expression if near screen border
                    var top = expressionRect.bottom + margin
                    if (top + scaledHeight > viewportHeight - 50f) {
                        top = max(14f, expressionRect.top - scaledHeight - margin)
                    }
                    val right = min(expressionRect.right, viewportWidth - 14f)
                    val left = max(14f, right - badgeWidth)
                    RectBounds(left, top, left + badgeWidth, top + scaledHeight)
                }
            }

            ExpressionLayout.VERTICAL_COLUMN -> {
                // In column arithmetic, expressionRect encompasses 2-4 rows plus separator.
                // Scale badge height to single-row height
                val rowHeightEst = expressionRect.height * 0.35f
                val scaledHeight = (rowHeightEst * 1.05f).coerceIn(34f, 48f)
                val charWidth = scaledHeight * 0.44f
                val horizontalPadding = scaledHeight * 0.60f
                val desiredWidth = textLength * charWidth + horizontalPadding
                val maxAllowedWidth = max(contentRect.width * 1.4f, 85f)
                val badgeWidth = desiredWidth.coerceIn(44f, maxAllowedWidth)

                // Place directly below the expression (under separator line or bottom row)
                val verticalMargin = (expressionRect.height * 0.05f).coerceIn(4f, 10f)
                var top = expressionRect.bottom + verticalMargin

                // If clipping viewport bottom, flip above the expression
                if (top + scaledHeight > viewportHeight - 50f) {
                    top = max(14f, expressionRect.top - scaledHeight - verticalMargin)
                }

                // Horizontally align strictly to contentRect (digits column), NOT expressionRect (which includes leftmost operator)
                var left = contentRect.centerX - badgeWidth / 2f
                left = max(14f, min(viewportWidth - badgeWidth - 14f, left))

                RectBounds(left, top, left + badgeWidth, top + scaledHeight)
            }
        }
    }
}
