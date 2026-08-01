package com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

internal enum class TokenType {
    PUNCTUATION,
    PROPERTY_KEY,
    STRING_VALUE,
    NUMBER_VALUE,
    KEYWORD_VALUE,
    TAG_NAME,
    ATTRIBUTE_NAME,
    COMMENT,
    PLAIN_TEXT
}

internal object TokenMaker {

    fun isOnlyWhitespaceBetween(text: String, startInclusive: Int, endExclusive: Int): Boolean {
        for (i in startInclusive until endExclusive) {
            val c = text[i]
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return false
            }
        }
        return true
    }

    fun buildAnnotatedStringFromTokens(
        tokens: List<Pair<IntRange, TokenType>>,
        lineText: String
    ): AnnotatedString {
        return AnnotatedString.Builder().apply {
            append(lineText)
            for ((range, type) in tokens) {
                val color = when (type) {
                    TokenType.PUNCTUATION -> EditorColors.TokenPunctuation
                    TokenType.PROPERTY_KEY -> EditorColors.TokenProperty
                    TokenType.STRING_VALUE -> EditorColors.TokenString
                    TokenType.NUMBER_VALUE -> EditorColors.TokenNumber
                    TokenType.KEYWORD_VALUE -> EditorColors.TokenKeyword
                    TokenType.TAG_NAME -> EditorColors.TokenTag
                    TokenType.ATTRIBUTE_NAME -> EditorColors.TokenAttribute
                    TokenType.COMMENT -> EditorColors.TokenComment
                    TokenType.PLAIN_TEXT -> EditorColors.TokenPlainText
                }
                addStyle(SpanStyle(color = color), range.first, range.last + 1)
            }
        }.toAnnotatedString()
    }
}
