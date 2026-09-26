package com.calclens.math

import kotlin.math.abs

class MathEvaluator {
    private val maxSafeResult = 1e15

    fun evaluate(node: ASTNode): Double {
        return evaluateNode(node)
    }

    private fun evaluateNode(node: ASTNode): Double {
        return when (node) {
            is ASTNode.NumberLiteral -> node.value

            is ASTNode.UnaryOperation -> {
                if (node.operator == '-') {
                    -evaluateNode(node.operand)
                } else {
                    throw IllegalArgumentException("Unsupported unary operator '${node.operator}'")
                }
            }

            is ASTNode.BinaryOperation -> {
                val left = evaluateNode(node.left)
                val right = evaluateNode(node.right)

                val result = when (node.operator) {
                    '+' -> left + right
                    '-' -> left - right
                    '*' -> left * right
                    '/' -> {
                        if (right == 0.0 || abs(right) < 1e-15) {
                            throw ArithmeticException("DIVISION_BY_ZERO")
                        }
                        left / right
                    }
                    else -> throw IllegalArgumentException("Unsupported operator '${node.operator}'")
                }

                if (!result.isFinite() || abs(result) > maxSafeResult) {
                    throw ArithmeticException("OVERFLOW")
                }

                result
            }
        }
    }

    fun formatResult(value: Double): String {
        return if (value % 1.0 == 0.0 && abs(value) < 1e12) {
            value.toLong().toString()
        } else {
            val rounded = String.format(java.util.Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
            rounded
        }
    }
}
