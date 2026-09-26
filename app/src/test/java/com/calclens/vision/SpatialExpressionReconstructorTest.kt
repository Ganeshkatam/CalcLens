package com.calclens.vision

import com.calclens.math.MathEngine
import com.calclens.math.MathResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialExpressionReconstructorTest {

    @Test
    fun testHorizontalExpressionPreserved() {
        val lines = listOf(
            RawTextLine(
                rawText = "27 × 14",
                normalizedText = "27 * 14",
                bounds = RectBounds(0.2f, 0.4f, 0.8f, 0.46f),
                confidence = 0.95f
            )
        )

        var idCount = 0
        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-${++idCount}" }

        assertEquals(1, candidates.size)
        assertEquals("27 * 14", candidates[0].normalizedText)

        val result = MathEngine.evaluate(candidates[0].normalizedText)
        assertTrue(result is MathResult.Success)
        assertEquals("378", (result as MathResult.Success).formatted)
    }

    @Test
    fun testHorizontalDecimalAndSignedArithmetic() {
        // "3.75 + 8.6"
        val decLine = listOf(
            RawTextLine(
                rawText = "3.75 + 8.6",
                normalizedText = "3.75 + 8.6",
                bounds = RectBounds(0.2f, 0.4f, 0.8f, 0.46f),
                confidence = 0.95f
            )
        )
        val decCand = SpatialExpressionReconstructor.reconstruct(decLine) { "test-1" }
        assertEquals(1, decCand.size)
        val decRes = MathEngine.evaluate(decCand[0].normalizedText)
        assertEquals("12.35", (decRes as MathResult.Success).formatted)

        // "-25 + 17"
        val signedLine = listOf(
            RawTextLine(
                rawText = "-25 + 17",
                normalizedText = "-25 + 17",
                bounds = RectBounds(0.2f, 0.4f, 0.8f, 0.46f),
                confidence = 0.95f
            )
        )
        val signedCand = SpatialExpressionReconstructor.reconstruct(signedLine) { "test-2" }
        assertEquals(1, signedCand.size)
        val signedRes = MathEngine.evaluate(signedCand[0].normalizedText)
        assertEquals("-8", (signedRes as MathResult.Success).formatted)
    }

    @Test
    fun testHorizontalParenthesesAndPrecedence() {
        // "(15 + 5) * 3"
        val parenLine = listOf(
            RawTextLine(
                rawText = "(15 + 5) × 3",
                normalizedText = "(15 + 5) * 3",
                bounds = RectBounds(0.2f, 0.4f, 0.8f, 0.46f),
                confidence = 0.95f
            )
        )
        val parenCand = SpatialExpressionReconstructor.reconstruct(parenLine) { "test-1" }
        assertEquals(1, parenCand.size)
        val parenRes = MathEngine.evaluate(parenCand[0].normalizedText)
        assertEquals("60", (parenRes as MathResult.Success).formatted)

        // "2 + 3 * 4"
        val precLine = listOf(
            RawTextLine(
                rawText = "2 + 3 × 4",
                normalizedText = "2 + 3 * 4",
                bounds = RectBounds(0.2f, 0.4f, 0.8f, 0.46f),
                confidence = 0.95f
            )
        )
        val precCand = SpatialExpressionReconstructor.reconstruct(precLine) { "test-2" }
        assertEquals(1, precCand.size)
        val precRes = MathEngine.evaluate(precCand[0].normalizedText)
        assertEquals("14", (precRes as MathResult.Success).formatted)
    }

    @Test
    fun testColumnMultiDigitAddition() {
        // 125
        // + 87
        val lines = listOf(
            RawTextLine(
                rawText = "125",
                normalizedText = "125",
                bounds = RectBounds(0.40f, 0.20f, 0.60f, 0.25f),
                confidence = 0.95f
            ),
            RawTextLine(
                rawText = "+ 87",
                normalizedText = "+ 87",
                bounds = RectBounds(0.38f, 0.26f, 0.60f, 0.31f),
                confidence = 0.94f
            )
        )

        var idCount = 0
        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-${++idCount}" }

        assertEquals(1, candidates.size)
        assertEquals("125 + 87", candidates[0].normalizedText)

        val result = MathEngine.evaluate(candidates[0].normalizedText)
        assertTrue(result is MathResult.Success)
        assertEquals("212", (result as MathResult.Success).formatted)
    }

    @Test
    fun testColumnMultiplication() {
        //   48
        // × 27
        val lines = listOf(
            RawTextLine(
                rawText = "48",
                normalizedText = "48",
                bounds = RectBounds(0.40f, 0.20f, 0.58f, 0.25f),
                confidence = 0.95f
            ),
            RawTextLine(
                rawText = "× 27",
                normalizedText = "* 27",
                bounds = RectBounds(0.36f, 0.26f, 0.58f, 0.31f),
                confidence = 0.92f
            )
        )

        var idCount = 0
        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-${++idCount}" }

        assertEquals(1, candidates.size)
        assertEquals("48 * 27", candidates[0].normalizedText)

        val result = MathEngine.evaluate(candidates[0].normalizedText)
        assertTrue(result is MathResult.Success)
        assertEquals("1296", (result as MathResult.Success).formatted)
    }

    @Test
    fun testColumnWithSeparatorLine() {
        //   125
        // +  87
        // -----
        val lines = listOf(
            RawTextLine(
                rawText = "125",
                normalizedText = "125",
                bounds = RectBounds(0.40f, 0.20f, 0.60f, 0.25f),
                confidence = 0.95f
            ),
            RawTextLine(
                rawText = "+ 87",
                normalizedText = "+ 87",
                bounds = RectBounds(0.38f, 0.26f, 0.60f, 0.31f),
                confidence = 0.94f
            ),
            RawTextLine(
                rawText = "-----",
                normalizedText = "-----",
                bounds = RectBounds(0.35f, 0.32f, 0.62f, 0.34f),
                confidence = 0.90f
            )
        )

        var idCount = 0
        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-${++idCount}" }

        assertEquals(1, candidates.size)
        assertEquals("125 + 87", candidates[0].normalizedText)
    }

    @Test
    fun testNonMathProseIgnored() {
        // "Chapter" / "8" / "Pages"
        val lines = listOf(
            RawTextLine(
                rawText = "Chapter",
                normalizedText = "",
                bounds = RectBounds(0.20f, 0.20f, 0.40f, 0.25f),
                confidence = 0.95f
            ),
            RawTextLine(
                rawText = "8",
                normalizedText = "8",
                bounds = RectBounds(0.20f, 0.26f, 0.25f, 0.31f),
                confidence = 0.95f
            ),
            RawTextLine(
                rawText = "Pages",
                normalizedText = "",
                bounds = RectBounds(0.20f, 0.32f, 0.35f, 0.37f),
                confidence = 0.95f
            )
        )

        var idCount = 0
        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-${++idCount}" }

        // Must NOT guess or combine into arithmetic!
        assertEquals(0, candidates.size)
    }

    @Test
    fun testLowConfidenceRejected() {
        val lines = listOf(
            RawTextLine(
                rawText = "27 × 14",
                normalizedText = "27 * 14",
                bounds = RectBounds(0.2f, 0.4f, 0.8f, 0.46f),
                confidence = 0.45f // Low confidence
            )
        )

        val candidates = SpatialExpressionReconstructor.reconstruct(lines, minConfidence = 0.60f) { "test-1" }
        assertEquals(0, candidates.size)
    }
}
