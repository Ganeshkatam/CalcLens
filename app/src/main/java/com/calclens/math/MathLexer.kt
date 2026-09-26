package com.calclens.math

class MathLexer(private val input: String) {
    private var pos: Int = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()

        while (pos < input.length) {
            val char = input[pos]

            if (char.isWhitespace()) {
                pos++
                continue
            }

            if (char.isDigit() || char == '.') {
                tokens.add(readNumber())
                continue
            }

            when (char) {
                '+' -> tokens.add(Token(TokenType.PLUS, "+", pos++))
                '-' -> tokens.add(Token(TokenType.MINUS, "-", pos++))
                '*' -> tokens.add(Token(TokenType.MULTIPLY, "*", pos++))
                '/' -> tokens.add(Token(TokenType.DIVIDE, "/", pos++))
                '(' -> tokens.add(Token(TokenType.LPAREN, "(", pos++))
                ')' -> tokens.add(Token(TokenType.RPAREN, ")", pos++))
                else -> throw IllegalArgumentException("Unexpected character '$char' at position $pos")
            }
        }

        tokens.add(Token(TokenType.EOF, "", pos))
        return tokens
    }

    private fun readNumber(): Token {
        val start = pos
        var hasDot = false

        while (pos < input.length) {
            val c = input[pos]
            if (c.isDigit()) {
                pos++
            } else if (c == '.' && !hasDot) {
                hasDot = true
                pos++
            } else {
                break
            }
        }

        val value = input.substring(start, pos)
        return Token(TokenType.NUMBER, value, start)
    }
}
