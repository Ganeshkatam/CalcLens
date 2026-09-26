package com.calclens.overlay

import com.calclens.vision.ExpressionLayout
import com.calclens.vision.RectBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoordinateTransformerTest {

    private val transformer = CoordinateTransformer(
        viewportWidth = 1080f,
        viewportHeight = 1920f,
        sensorWidth = 1080f,
        sensorHeight = 1920f
    )

    @Test
    fun testHorizontalExpressionPlacementRight() {
        // "2 x 4 =" positioned in center of screen
        val expr = RectBounds(200f, 300f, 450f, 340f)
        val badge = transformer.computeBadgePlacement(
            expressionRect = expr,
            contentRect = expr,
            layout = ExpressionLayout.HORIZONTAL,
            textLength = 2
        )

        // 1. Badge must be placed immediately to the right of the expression
        assertTrue("Badge left (${badge.left}) must be >= expr right (${expr.right})", badge.left >= expr.right)

        // 2. Badge must be vertically aligned with expression center line
        assertEquals("Badge centerY must align with expression centerY", expr.centerY, badge.centerY, 1.0f)
    }

    @Test
    fun testHorizontalExpressionNearRightEdgeFallsBackToBelow() {
        // Expression near right edge of 1080p screen
        val expr = RectBounds(750f, 300f, 1050f, 340f)
        val badge = transformer.computeBadgePlacement(
            expressionRect = expr,
            contentRect = expr,
            layout = ExpressionLayout.HORIZONTAL,
            textLength = 2
        )

        // Cannot fit to the right of 1050f on a 1080f screen -> must fall back below
        assertTrue("Badge top (${badge.top}) must be below expr bottom (${expr.bottom})", badge.top >= expr.bottom)
        assertTrue("Badge right (${badge.right}) must stay within screen", badge.right <= 1080f - 14f)
    }

    @Test
    fun testVerticalColumnPlacementCenteredOnDigitsNotOperator() {
        // Worksheet vertical subtraction:
        //   45
        // - 19
        // ----
        // Operator '-' on left pulls total expression left to 100f
        val expr = RectBounds(100f, 300f, 200f, 420f)
        // Digits '45' and '19' column is strictly between 135f and 200f (center = 167.5f)
        val digitsCol = RectBounds(135f, 300f, 200f, 420f)

        val badge = transformer.computeBadgePlacement(
            expressionRect = expr,
            contentRect = digitsCol,
            layout = ExpressionLayout.VERTICAL_COLUMN,
            textLength = 2
        )

        // 1. Badge must be positioned below the expression
        assertTrue("Badge top (${badge.top}) must be below expr bottom (${expr.bottom})", badge.top >= expr.bottom)

        // 2. Badge centerX must be anchored to digits column (167.5f), NOT expression center (150f)
        assertEquals("Badge center must match digits column center", digitsCol.centerX, badge.centerX, 2.0f)
        assertTrue("Badge must NOT be pulled towards the operator", abs(badge.centerX - expr.centerX) > 10f)
    }

    @Test
    fun testVerticalColumnNearBottomFlipsAbove() {
        // Column near the bottom of 1920p screen
        val expr = RectBounds(200f, 1820f, 320f, 1890f)
        val badge = transformer.computeBadgePlacement(
            expressionRect = expr,
            contentRect = expr,
            layout = ExpressionLayout.VERTICAL_COLUMN,
            textLength = 2
        )

        // Badge must flip above the expression so it is not cut off by the screen bottom
        assertTrue("Badge bottom (${badge.bottom}) must be above expr top (${expr.top})", badge.bottom <= expr.top)
    }

    @Test
    fun testMultiColumnGridPlacementDoesNotCollide() {
        // 4 columns on worksheet:
        // 45       65       74       41
        // -19      -27      -38      -19
        val cols = listOf(
            RectBounds(100f, 500f, 200f, 620f) to RectBounds(135f, 500f, 200f, 620f),
            RectBounds(230f, 500f, 330f, 620f) to RectBounds(265f, 500f, 330f, 620f),
            RectBounds(360f, 500f, 460f, 620f) to RectBounds(395f, 500f, 460f, 620f),
            RectBounds(490f, 500f, 590f, 620f) to RectBounds(525f, 500f, 590f, 620f)
        )

        val badges = cols.map { (expr, content) ->
            transformer.computeBadgePlacement(
                expressionRect = expr,
                contentRect = content,
                layout = ExpressionLayout.VERTICAL_COLUMN,
                textLength = 2
            )
        }

        // Each badge must sit neatly under its column with zero overlap
        for (i in 0 until badges.size - 1) {
            assertTrue(
                "Badge $i right (${badges[i].right}) must be less than Badge ${i+1} left (${badges[i+1].left})",
                badges[i].right < badges[i+1].left
            )
        }
    }
}
