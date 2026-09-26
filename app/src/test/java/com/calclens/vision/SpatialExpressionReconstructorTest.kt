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
    fun testColumnMultiplicationWithLetterX() {
        //   2
        // X 4
        val lines = listOf(
            RawTextLine(
                rawText = "2",
                normalizedText = ExpressionNormalizer.normalize("2"),
                bounds = RectBounds(0.40f, 0.20f, 0.58f, 0.25f),
                confidence = 0.95f,
                pixelHeight = 25f
            ),
            RawTextLine(
                rawText = "X 4",
                normalizedText = ExpressionNormalizer.normalize("X 4"),
                bounds = RectBounds(0.36f, 0.26f, 0.58f, 0.31f),
                confidence = 0.92f,
                pixelHeight = 25f
            )
        )

        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-x-mult" }
        assertEquals(1, candidates.size)
        assertEquals("2 * 4", candidates[0].normalizedText)

        val res = MathEngine.evaluate(candidates[0].normalizedText) as MathResult.Success
        assertEquals("8", res.formatted)
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

    @Test
    fun testWorksheetFourColumnsSubtractionWithSeparators() {
        // Simulates the exact layout from the worksheet screenshot:
        // Col 1: 45 / - 19 / ----
        // Col 2: 65 / - 27 / ----
        // Col 3: 74 / - 38 / ----
        // Col 4: 41 / - 19 / ----
        val lines = listOf(
            // Column 1
            RawTextLine("45", "45", RectBounds(0.12f, 0.40f, 0.22f, 0.44f), 0.95f),
            RawTextLine("- 19", "- 19", RectBounds(0.10f, 0.45f, 0.22f, 0.49f), 0.94f),
            RawTextLine("----", "----", RectBounds(0.10f, 0.50f, 0.24f, 0.51f), 0.90f),

            // Column 2
            RawTextLine("65", "65", RectBounds(0.35f, 0.40f, 0.45f, 0.44f), 0.95f),
            RawTextLine("- 27", "- 27", RectBounds(0.33f, 0.45f, 0.45f, 0.49f), 0.93f),
            RawTextLine("----", "----", RectBounds(0.33f, 0.50f, 0.47f, 0.51f), 0.91f),

            // Column 3
            RawTextLine("74", "74", RectBounds(0.58f, 0.40f, 0.68f, 0.44f), 0.96f),
            RawTextLine("- 38", "- 38", RectBounds(0.56f, 0.45f, 0.68f, 0.49f), 0.95f),
            RawTextLine("----", "----", RectBounds(0.56f, 0.50f, 0.70f, 0.51f), 0.92f),

            // Column 4
            RawTextLine("41", "41", RectBounds(0.80f, 0.40f, 0.90f, 0.44f), 0.94f),
            RawTextLine("- 19", "- 19", RectBounds(0.78f, 0.45f, 0.90f, 0.49f), 0.93f),
            RawTextLine("----", "----", RectBounds(0.78f, 0.50f, 0.92f, 0.51f), 0.90f)
        )

        var idCount = 0
        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "col-${++idCount}" }

        // Must reconstruct all 4 columns independently
        assertEquals(4, candidates.size)

        // Sorted by X-coordinate
        val sorted = candidates.sortedBy { it.boundingBox.left }

        assertEquals("45 - 19", sorted[0].normalizedText)
        assertEquals("65 - 27", sorted[1].normalizedText)
        assertEquals("74 - 38", sorted[2].normalizedText)
        assertEquals("41 - 19", sorted[3].normalizedText)

        // Evaluate deterministically
        val res1 = MathEngine.evaluate(sorted[0].normalizedText) as MathResult.Success
        val res2 = MathEngine.evaluate(sorted[1].normalizedText) as MathResult.Success
        val res3 = MathEngine.evaluate(sorted[2].normalizedText) as MathResult.Success
        val res4 = MathEngine.evaluate(sorted[3].normalizedText) as MathResult.Success

        assertEquals("26", res1.formatted)
        assertEquals("38", res2.formatted)
        assertEquals("36", res3.formatted)
        assertEquals("22", res4.formatted)

        // Verify bounding boxes absorbed separator lines (bottom should reach separator line ~0.51f)
        for (cand in sorted) {
            assertTrue("Bounding box bottom should absorb separator: ${cand.boundingBox.bottom}", cand.boundingBox.bottom >= 0.50f)
        }
    }

    @Test
    fun testColumnWithSeparateOperatorLine() {
        // Simulates ML Kit output where '+' is its own OCR line separate from '10':
        // Line 1: '79'
        // Line 2: '+'
        // Line 3: '10'
        val lines = listOf(
            RawTextLine("79", "79", RectBounds(0.40f, 0.20f, 0.55f, 0.25f), 0.95f),
            RawTextLine("+", "+", RectBounds(0.35f, 0.27f, 0.39f, 0.31f), 0.88f),
            RawTextLine("10", "10", RectBounds(0.41f, 0.27f, 0.55f, 0.31f), 0.94f)
        )

        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-split" }
        assertEquals(1, candidates.size)
        assertEquals("79 + 10", candidates[0].normalizedText)

        val res = MathEngine.evaluate(candidates[0].normalizedText) as MathResult.Success
        assertEquals("89", res.formatted)
    }

    @Test
    fun testThreeTermColumnAdditionWithProblemLabelAndSeparator() {
        // Reproduces the exact user screenshot:
        // (i)   12
        //     + 15
        //     + 13
        //     ────
        val lines = listOf(
            RawTextLine("(i)", "(i)", RectBounds(0.08f, 0.18f, 0.18f, 0.24f), 0.92f),
            RawTextLine("12", "12", RectBounds(0.38f, 0.18f, 0.66f, 0.25f), 0.96f),
            RawTextLine("+ 15", "+ 15", RectBounds(0.34f, 0.27f, 0.66f, 0.34f), 0.95f),
            RawTextLine("+ 13", "+ 13", RectBounds(0.34f, 0.36f, 0.66f, 0.43f), 0.94f),
            RawTextLine("────", "----", RectBounds(0.32f, 0.45f, 0.73f, 0.47f), 0.90f)
        )

        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-3term" }

        assertEquals(1, candidates.size)
        assertEquals("12 + 15 + 13", candidates[0].normalizedText)

        val res = MathEngine.evaluate(candidates[0].normalizedText) as MathResult.Success
        assertEquals("40", res.formatted)

        // Bounding box must cover all rows and the separator line
        val bbox = candidates[0].boundingBox
        assertTrue("Top should reach first operand (12): ${bbox.top}", bbox.top <= 0.18f)
        assertTrue("Bottom should absorb separator line (────): ${bbox.bottom}", bbox.bottom >= 0.47f)
    }

    @Test
    fun testArbitraryLengthColumnArithmetic() {
        // 4 rows: 12 + 15 + 13 + 28 = 68
        val lines = listOf(
            RawTextLine("12", "12", RectBounds(0.40f, 0.10f, 0.60f, 0.15f), 0.95f),
            RawTextLine("+ 15", "+ 15", RectBounds(0.38f, 0.17f, 0.60f, 0.22f), 0.95f),
            RawTextLine("+ 13", "+ 13", RectBounds(0.38f, 0.24f, 0.60f, 0.29f), 0.95f),
            RawTextLine("+ 28", "+ 28", RectBounds(0.38f, 0.31f, 0.60f, 0.36f), 0.95f),
            RawTextLine("────", "----", RectBounds(0.35f, 0.38f, 0.63f, 0.40f), 0.90f)
        )

        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-4term" }

        assertEquals(1, candidates.size)
        assertEquals("12 + 15 + 13 + 28", candidates[0].normalizedText)

        val res = MathEngine.evaluate(candidates[0].normalizedText) as MathResult.Success
        assertEquals("68", res.formatted)
    }

    @Test
    fun testColumnWithSingleBottomPlusSign() {
        // Format where plus is only on the bottom line:
        //   12
        //   15
        // + 13
        // ────
        val lines = listOf(
            RawTextLine("12", "12", RectBounds(0.40f, 0.18f, 0.60f, 0.25f), 0.95f),
            RawTextLine("15", "15", RectBounds(0.40f, 0.27f, 0.60f, 0.34f), 0.95f),
            RawTextLine("+ 13", "+ 13", RectBounds(0.36f, 0.36f, 0.60f, 0.43f), 0.95f),
            RawTextLine("────", "----", RectBounds(0.34f, 0.45f, 0.62f, 0.47f), 0.90f)
        )

        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-bottom-op" }

        assertEquals(1, candidates.size)
        assertEquals("12 + 15 + 13", candidates[0].normalizedText)

        val res = MathEngine.evaluate(candidates[0].normalizedText) as MathResult.Success
        assertEquals("40", res.formatted)
    }

    @Test
    fun testIncompleteFragmentRejected() {
        val lines = listOf(
            RawTextLine("(iv)", "(iv)", RectBounds(0.70f, 0.70f, 0.85f, 0.75f), 0.90f),
            RawTextLine("+", "+", RectBounds(0.75f, 0.80f, 0.80f, 0.85f), 0.85f)
        )
        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "test-frag" }
        assertEquals(0, candidates.size)
    }

    @Test
    fun testTextTooFarFlaggedAndRejected() {
        // Characters are too small (pixelHeight = 8f, below 10px threshold)
        val lines = listOf(
            RawTextLine(
                rawText = "60 + 26",
                normalizedText = "60 + 26",
                bounds = RectBounds(0.40f, 0.45f, 0.60f, 0.46f),
                confidence = 0.90f,
                pixelHeight = 8f
            )
        )

        val result = SpatialExpressionReconstructor.reconstructWithDiagnostics(lines) { "test-too-far" }
        assertTrue("Should detect math text that is too far/small", result.hasTooFarText)
        assertEquals(0, result.candidates.size)
    }

    @Test
    fun testOperatingRangeAdequateResolutionAccepted() {
        // Characters have sufficient resolution (pixelHeight = 36f >= 16px)
        val lines = listOf(
            RawTextLine(
                rawText = "60 + 26",
                normalizedText = "60 + 26",
                bounds = RectBounds(0.30f, 0.40f, 0.70f, 0.46f),
                confidence = 0.95f,
                pixelHeight = 36f
            )
        )

        val result = SpatialExpressionReconstructor.reconstructWithDiagnostics(lines) { "test-adequate" }
        assertEquals(1, result.candidates.size)
        assertEquals("60 + 26", result.candidates[0].normalizedText)
    }

    @Test
    fun testCroppedBorderExpressionRejected() {
        // Line clipped by the top edge of the camera sensor (top = 0.002f <= 0.005f)
        val lines = listOf(
            RawTextLine(
                rawText = "27 × 14",
                normalizedText = "27 * 14",
                bounds = RectBounds(0.20f, 0.002f, 0.80f, 0.04f),
                confidence = 0.95f,
                pixelHeight = 30f
            )
        )

        val result = SpatialExpressionReconstructor.reconstructWithDiagnostics(lines) { "test-cropped" }
        assertEquals(0, result.candidates.size)
    }

    @Test
    fun testVerticalMultiplicationGridWorksheet() {
        // Multi-column vertical multiplication worksheet:
        // Col 1:  8 / × 3 / ──── -> 24
        // Col 2:  4 / × 4 / ──── -> 16
        // Col 3:  2 / × 4 / ──── -> 8
        val lines = listOf(
            // Column 1
            RawTextLine("8", "8", RectBounds(0.12f, 0.20f, 0.18f, 0.25f), 0.90f, 24f),
            RawTextLine("×", "*", RectBounds(0.08f, 0.27f, 0.11f, 0.32f), 0.45f, 24f),
            RawTextLine("3", "3", RectBounds(0.12f, 0.27f, 0.18f, 0.32f), 0.92f, 24f),
            RawTextLine("────", "----", RectBounds(0.07f, 0.34f, 0.20f, 0.35f), 0.90f, 6f),

            // Column 2
            RawTextLine("4", "4", RectBounds(0.42f, 0.20f, 0.48f, 0.25f), 0.88f, 24f),
            RawTextLine("×", "*", RectBounds(0.38f, 0.27f, 0.41f, 0.32f), 0.42f, 24f),
            RawTextLine("4", "4", RectBounds(0.42f, 0.27f, 0.48f, 0.32f), 0.89f, 24f),
            RawTextLine("────", "----", RectBounds(0.37f, 0.34f, 0.50f, 0.35f), 0.90f, 6f),

            // Column 3
            RawTextLine("2", "2", RectBounds(0.72f, 0.20f, 0.78f, 0.25f), 0.91f, 24f),
            RawTextLine("X", "*", RectBounds(0.68f, 0.27f, 0.71f, 0.32f), 0.40f, 24f),
            RawTextLine("4", "4", RectBounds(0.72f, 0.27f, 0.78f, 0.32f), 0.93f, 24f),
            RawTextLine("────", "----", RectBounds(0.67f, 0.34f, 0.80f, 0.35f), 0.90f, 6f)
        )

        val candidates = SpatialExpressionReconstructor.reconstruct(lines) { "grid-cand" }
        assertEquals(3, candidates.size)

        val sorted = candidates.sortedBy { it.boundingBox.left }
        assertEquals("8 * 3", sorted[0].normalizedText)
        assertEquals("4 * 4", sorted[1].normalizedText)
        assertEquals("2 * 4", sorted[2].normalizedText)

        assertEquals("24", (MathEngine.evaluate(sorted[0].normalizedText) as MathResult.Success).formatted)
        assertEquals("16", (MathEngine.evaluate(sorted[1].normalizedText) as MathResult.Success).formatted)
        assertEquals("8", (MathEngine.evaluate(sorted[2].normalizedText) as MathResult.Success).formatted)
    }
}
