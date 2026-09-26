package com.calclens.math

import com.calclens.vision.ExpressionNormalizer
import com.calclens.vision.MathRegionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionNormalizerTest {

    @Test
    fun testOperatorCanonicalization() {
        assertEquals("27 * 14", ExpressionNormalizer.normalize("27 × 14"))
        assertEquals("27 * 14", ExpressionNormalizer.normalize("27 x 14"))
        assertEquals("27 * 14", ExpressionNormalizer.normalize("27 X 14"))
        assertEquals("100 / 4", ExpressionNormalizer.normalize("100 ÷ 4"))
        assertEquals("100 / 4", ExpressionNormalizer.normalize("100 : 4"))
        assertEquals("50 - 17", ExpressionNormalizer.normalize("50 − 17"))
        assertEquals("125 + 87", ExpressionNormalizer.normalize("  125   +   87  "))
    }

    @Test
    fun testOcrDigitDisambiguation() {
        assertEquals("20 * 14", ExpressionNormalizer.normalize("2O * 14"))
        assertEquals("12 + 8", ExpressionNormalizer.normalize("l2 + 8"))
        assertEquals("100 / 4", ExpressionNormalizer.normalize("1OO / 4"))
    }

    @Test
    fun testMathRegionFilter() {
        assertTrue(MathRegionFilter.isViableArithmetic("27 * 14", 0.95f))
        assertTrue(MathRegionFilter.isViableArithmetic("125 + 87", 0.90f))
        assertFalse(MathRegionFilter.isViableArithmetic("27 * 14", 0.40f))
        assertFalse(MathRegionFilter.isViableArithmetic("not a math equation", 0.95f))
        // Incomplete / trailing operator rejection
        assertFalse(MathRegionFilter.isViableArithmetic("27 + ", 0.95f))
        assertFalse(MathRegionFilter.isViableArithmetic("27 *", 0.95f))
        assertFalse(MathRegionFilter.isViableArithmetic("+ 14", 0.95f))
        assertFalse(MathRegionFilter.isViableArithmetic("42", 0.95f))
    }

    @Test
    fun testNeverGuessUncertainExpressions() {
        // Corrupt or uncertain OCR must preserve invalid tokens and be rejected by MathRegionFilter
        val uncertain1 = ExpressionNormalizer.normalize("2? x 14")
        assertFalse("Uncertain expression '2? x 14' must be rejected", MathRegionFilter.isViableArithmetic(uncertain1))

        val uncertain2 = ExpressionNormalizer.normalize("2a + 3")
        assertFalse("Alphanumeric noise '2a + 3' must be rejected", MathRegionFilter.isViableArithmetic(uncertain2))

        val uncertain3 = ExpressionNormalizer.normalize("Chapter 8")
        assertFalse("Heading text 'Chapter 8' must be rejected", MathRegionFilter.isViableArithmetic(uncertain3))

        val uncertain4 = ExpressionNormalizer.normalize("12 + ?")
        assertFalse("Trailing question mark '12 + ?' must be rejected", MathRegionFilter.isViableArithmetic(uncertain4))

        val uncertain5 = ExpressionNormalizer.normalize("50% - 10")
        assertFalse("Unsupported symbol '50% - 10' must be rejected", MathRegionFilter.isViableArithmetic(uncertain5))
    }
}

