package com.calclens.math

sealed class MathResult {
    data class Success(val value: Double, val formatted: String) : MathResult()
    data class DivisionByZero(val message: String = "Division by zero is undefined") : MathResult()
    data class Overflow(val message: String = "Result exceeds supported numeric limits") : MathResult()
    data class SyntaxError(val message: String) : MathResult()
}

object MathEngine {
    private val evaluator = MathEvaluator()

    fun evaluate(expression: String): MathResult {
        val sanitized = expression.trim()
        if (sanitized.isEmpty()) {
            return MathResult.SyntaxError("Expression cannot be empty")
        }

        return try {
            val lexer = MathLexer(sanitized)
            val tokens = lexer.tokenize()

            val parser = MathParser(tokens)
            val ast = parser.parse()

            val rawResult = evaluator.evaluate(ast)
            val formatted = evaluator.formatResult(rawResult)

            MathResult.Success(rawResult, formatted)
        } catch (e: ArithmeticException) {
            when (e.message) {
                "DIVISION_BY_ZERO" -> MathResult.DivisionByZero()
                "OVERFLOW" -> MathResult.Overflow()
                else -> MathResult.SyntaxError(e.message ?: "Arithmetic error")
            }
        } catch (e: Exception) {
            MathResult.SyntaxError(e.message ?: "Syntax error")
        }
    }
}
