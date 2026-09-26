package com.calclens.overlay

import com.calclens.tracking.TrackingStatus
import com.calclens.vision.RectBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CollisionAvoidanceTest {

    @Test
    fun testNeverShiftsVerticallyOnWorksheetRow() {
        // 4 columns side by side like on the user's worksheet
        // Baseline Y = 500f, Badge Height = 56f, Top = 500f, Bottom = 556f
        val layouts = listOf(
            BadgeLayout(
                id = "col-1",
                badgeRect = RectBounds(100f, 500f, 200f, 556f),
                expressionRect = RectBounds(110f, 380f, 190f, 485f),
                result = "26",
                errorMessage = null,
                status = TrackingStatus.DISPLAYING,
                rawText = "45\n-19"
            ),
            BadgeLayout(
                id = "col-2",
                badgeRect = RectBounds(205f, 500f, 305f, 556f), // Close to col-1
                expressionRect = RectBounds(220f, 380f, 300f, 485f),
                result = "38",
                errorMessage = null,
                status = TrackingStatus.DISPLAYING,
                rawText = "65\n-27"
            ),
            BadgeLayout(
                id = "col-3",
                badgeRect = RectBounds(310f, 500f, 410f, 556f), // Close to col-2
                expressionRect = RectBounds(330f, 380f, 410f, 485f),
                result = "36",
                errorMessage = null,
                status = TrackingStatus.DISPLAYING,
                rawText = "74\n-38"
            ),
            BadgeLayout(
                id = "col-4",
                badgeRect = RectBounds(430f, 500f, 530f, 556f),
                expressionRect = RectBounds(440f, 380f, 520f, 485f),
                result = "22",
                errorMessage = null,
                status = TrackingStatus.DISPLAYING,
                rawText = "41\n-19"
            )
        )

        val resolved = CollisionAvoidance.resolveCollisions(layouts)

        assertEquals(4, resolved.size)

        // Rule 1: Y coordinates must NEVER be modified (zero vertical shifting)
        for (layout in resolved) {
            assertEquals("Badge top must stay anchored at 500f", 500f, layout.badgeRect.top, 0.001f)
            assertEquals("Badge bottom must stay anchored at 556f", 556f, layout.badgeRect.bottom, 0.001f)
        }

        // Rule 2: Order preserved from left to right
        for (i in 0 until resolved.size - 1) {
            assertTrue("Badges must maintain horizontal order", resolved[i].badgeRect.left < resolved[i + 1].badgeRect.left)
        }

        // Rule 3: Badges must stay within permitted anchor zone of their expression
        for (layout in resolved) {
            val anchorX = layout.expressionRect.centerX
            val badgeX = layout.badgeRect.centerX
            val maxAllowedShift = layout.expressionRect.width * 0.20f
            assertTrue("Badge center must remain within anchor zone of expression", abs(badgeX - anchorX) <= maxAllowedShift)
        }
    }
}
