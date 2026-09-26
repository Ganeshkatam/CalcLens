package com.calclens.math

sealed class ASTNode {
    data class NumberLiteral(val value: Double) : ASTNode()
    data class BinaryOperation(
        val operator: Char,
        val left: ASTNode,
        val right: ASTNode
    ) : ASTNode()
    data class UnaryOperation(
        val operator: Char,
        val operand: ASTNode
    ) : ASTNode()
}
