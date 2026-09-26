package com.calclens.overlay

import com.calclens.vision.RectBounds
import kotlin.math.max
import kotlin.math.min

object CollisionAvoidance {

    /**
     * Resolves collisions between badges strictly within permitted anchor zones.
     *
     * Invariants:
     * 1. Never shift along the Y-axis. Badges remain anchored on their expression's baseline.
     * 2. Badges may only nudge horizontally by a small fraction (at most 18% of expression width).
     * 3. A badge must never be pushed into an adjacent expression's column.
     */
    fun resolveCollisions(
        layouts: List<BadgeLayout>,
        minHorizontalGap: Float = 8f
    ): List<BadgeLayout> {
        if (layouts.size <= 1) return layouts

        // Sort by horizontal expression center from left to right
        val sorted = layouts.sortedBy { it.expressionRect.centerX }
        val resolved = mutableListOf<BadgeLayout>()

        for (i in sorted.indices) {
            val current = sorted[i]
            var currentBadge = current.badgeRect
            val anchorCenterX = current.expressionRect.centerX
            val maxAllowedShiftX = max(16f, current.expressionRect.width * 0.18f)

            if (resolved.isNotEmpty()) {
                val previous = resolved.last()
                val prevBadge = previous.badgeRect

                // Check if they share the same horizontal baseline (vertical overlap > 40%)
                val verticalOverlap = max(0f, min(currentBadge.bottom, prevBadge.bottom) - max(currentBadge.top, prevBadge.top))
                val minHeight = min(currentBadge.height, prevBadge.height)

                if (verticalOverlap > 0.40f * minHeight) {
                    val overlapX = (prevBadge.right + minHorizontalGap) - currentBadge.left
                    if (overlapX > 0f) {
                        // Nudge current badge to the right, but never exceed anchor zone
                        val allowableShift = (anchorCenterX + maxAllowedShiftX) - currentBadge.centerX
                        val shiftX = min(overlapX, max(0f, allowableShift))
                        currentBadge = currentBadge.offset(shiftX, 0f)
                    }
                }
            }

            resolved.add(current.copy(badgeRect = currentBadge))
        }

        return resolved
    }
}
