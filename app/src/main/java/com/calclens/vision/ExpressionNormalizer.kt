package com.calclens.vision

object ExpressionNormalizer {

    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        var text = raw.trim()

        // 1. Canonicalize Unicode and common operator symbols
        text = text
            .replace('×', '*')
            .replace('•', '*')
            .replace('÷', '/')
            .replace(':', '/')
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('＋', '+')

        // Normalize letter 'x' or 'X' to '*' when at start/end of line or adjacent to digits/spaces
        text = text.replace(Regex("(?<=^|[0-9\\s])[xX](?=[0-9\\s]|$)"), "*")

        // 2. Contextual OCR digit confusion fixes
        if (text.contains(Regex("[+\\-*/]"))) {
            // Replace O/o with 0 within numeric sequences
            text = text.replace(Regex("\\b[0-9]*[Oo]+[0-9]*\\b")) { matchResult ->
                matchResult.value.replace('O', '0').replace('o', '0')
            }

            // Replace l, I, or | with 1 within numeric sequences
            text = text.replace(Regex("\\b[0-9]*[lI|]+[0-9]*\\b")) { matchResult ->
                matchResult.value.replace('l', '1').replace('I', '1').replace('|', '1')
            }
        }

        // 3. Collapse whitespace
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }
}
