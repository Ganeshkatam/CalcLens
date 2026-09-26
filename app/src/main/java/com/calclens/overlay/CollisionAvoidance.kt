package com.calclens.overlay

import android.graphics.RectF

object CollisionAvoidance {

    fun resolveCollisions(layouts: List<BadgeLayout>, padding: Float = 16f): List<BadgeLayout> {
        if (layouts.size <= 1) return layouts

        val resolved = mutableListOf<BadgeLayout>()

        for (layout in layouts) {
            val rect = RectF(layout.badgeRect)

            for (existing in resolved) {
                if (RectF.intersects(rect, existing.badgeRect)) {
                    val shiftY = (existing.badgeRect.bottom - rect.top) + padding
                    rect.offset(0f, shiftY)
                }
            }

            resolved.add(layout.copy(badgeRect = rect))
        }

        return resolved
    }
}
