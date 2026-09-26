package com.calclens.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathEngineTest {

    @Test
    fun testCoreMultiplication() {
        val result = MathEngine.evaluate("27 * 14")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(378.0, success.value, 0.0001)
        assertEquals("378", success.formatted)
    }

    @Test
    fun testCoreAddition() {
        val result = MathEngine.evaluate("125 + 87")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(212.0, success.value, 0.0001)
        assertEquals("212", success.formatted)
    }

    @Test
    fun testCoreSubtraction() {
        val result = MathEngine.evaluate("50 - 17")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(33.0, success.value, 0.0001)
        assertEquals("33", success.formatted)
    }

    @Test
    fun testCoreDivision() {
        val result = MathEngine.evaluate("100 / 4")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(25.0, success.value, 0.0001)
        assertEquals("25", success.formatted)
    }

    @Test
    fun testDecimalDivision() {
        val result = MathEngine.evaluate("10 / 4")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(2.5, success.value, 0.0001)
        assertEquals("2.5", success.formatted)
    }

    @Test
    fun testOperatorPrecedence() {
        // 2 + (3 * 4) = 14, not (2 + 3) * 4 = 20
        val result = MathEngine.evaluate("2 + 3 * 4")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(14.0, success.value, 0.0001)
    }

    @Test
    fun testLeftAssociativity() {
        // (10 - 4) - 2 = 4
        val result = MathEngine.evaluate("10 - 4 - 2")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(4.0, success.value, 0.0001)
    }

    @Test
    fun testParentheses() {
        val result = MathEngine.evaluate("(2 + 3) * 4")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(20.0, success.value, 0.0001)
    }

    @Test
    fun testNegativeResult() {
        val result = MathEngine.evaluate("5 - 12")
        assertTrue(result is MathResult.Success)
        val success = result as MathResult.Success
        assertEquals(-7.0, success.value, 0.0001)
    }

    @Test
    fun testDivisionByZero() {
        val result = MathEngine.evaluate("100 / 0")
        assertTrue(result is MathResult.DivisionByZero)
    }

    @Test
    fun testSyntaxError() {
        val result = MathEngine.evaluate("27 + ")
        assertTrue(result is MathResult.SyntaxError)
    }
}
