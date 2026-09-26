package com.calclens.math

enum class TokenType {
    NUMBER,
    PLUS,
    MINUS,
    MULTIPLY,
    DIVIDE,
    LPAREN,
    RPAREN,
    EOF
}

data class Token(
    val type: TokenType,
    val value: String,
    val position: Int
)
