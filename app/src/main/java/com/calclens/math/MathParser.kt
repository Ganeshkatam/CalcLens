package com.calclens.math

class MathParser(private val tokens: List<Token>) {
    private var current: Int = 0
    private var recursionDepth: Int = 0
    private val maxRecursionDepth: Int = 32

    fun parse(): ASTNode {
        if (peek().type == TokenType.EOF) {
            throw IllegalArgumentException("Empty expression")
        }

        val node = parseAdditive()

        if (peek().type != TokenType.EOF) {
            val unexpected = peek()
            throw IllegalArgumentException("Unexpected token '${unexpected.value}' at position ${unexpected.position}")
        }

        return node
    }

    private fun parseAdditive(): ASTNode {
        checkRecursion()
        recursionDepth++
        try {
            var left = parseMultiplicative()

            while (match(TokenType.PLUS, TokenType.MINUS)) {
                val opToken = previous()
                val operator = opToken.value[0]
                val right = parseMultiplicative()
                left = ASTNode.BinaryOperation(operator, left, right)
            }

            return left
        } finally {
            recursionDepth--
        }
    }

    private fun parseMultiplicative(): ASTNode {
        checkRecursion()
        recursionDepth++
        try {
            var left = parsePrimary()

            while (match(TokenType.MULTIPLY, TokenType.DIVIDE)) {
                val opToken = previous()
                val operator = opToken.value[0]
                val right = parsePrimary()
                left = ASTNode.BinaryOperation(operator, left, right)
            }

            return left
        } finally {
            recursionDepth--
        }
    }

    private fun parsePrimary(): ASTNode {
        checkRecursion()
        recursionDepth++
        try {
            if (match(TokenType.MINUS)) {
                val operand = parsePrimary()
                return ASTNode.UnaryOperation('-', operand)
            }

            if (match(TokenType.NUMBER)) {
                val numToken = previous()
                val value = numToken.value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid number literal '${numToken.value}' at position ${numToken.position}")
                return ASTNode.NumberLiteral(value)
            }

            if (match(TokenType.LPAREN)) {
                val inner = parseAdditive()
                consume(TokenType.RPAREN, "Expected closing ')' after expression")
                return inner
            }

            val unexpected = peek()
            throw IllegalArgumentException("Expected expression but found '${unexpected.value.ifEmpty { "EOF" }}' at position ${unexpected.position}")
        } finally {
            recursionDepth--
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens.getOrElse(current) { Token(TokenType.EOF, "", 0) }

    private fun previous(): Token = tokens[current - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        val token = peek()
        throw IllegalArgumentException("$message at position ${token.position}")
    }

    private fun checkRecursion() {
        if (recursionDepth > maxRecursionDepth) {
            throw IllegalStateException("Maximum expression recursion depth exceeded")
        }
    }
}
