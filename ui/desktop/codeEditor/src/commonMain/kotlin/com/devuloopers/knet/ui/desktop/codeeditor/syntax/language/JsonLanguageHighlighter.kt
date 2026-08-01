package com.devuloopers.knet.ui.desktop.codeeditor.syntax.language

import androidx.compose.ui.text.AnnotatedString
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenMaker
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenType

internal class JsonLanguageHighlighter : CodeLanguageHighlighter {

    override val languageId: String = "json"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val braceStack = mutableListOf<Int>()
        val bracketStack = mutableListOf<Int>()

        for (index in lines.indices) {
            val line = lines[index]
            for (char in line) {
                when (char) {
                    '{' -> braceStack.add(index)
                    '}' -> {
                        if (braceStack.isNotEmpty()) {
                            val start = braceStack.removeAt(braceStack.lastIndex)
                            if (index > start) result[start] = index
                        }
                    }
                    '[' -> bracketStack.add(index)
                    ']' -> {
                        if (bracketStack.isNotEmpty()) {
                            val start = bracketStack.removeAt(bracketStack.lastIndex)
                            if (index > start) result[start] = index
                        }
                    }
                }
            }
        }
        return result
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex in lines.indices) {
            val line = lines[endLineIndex].trim()
            if (line.endsWith("]")) return "]"
        }
        return "}"
    }

    override fun highlightLine(lineText: String): AnnotatedString {
        if (lineText.isBlank()) return AnnotatedString(lineText)

        val tokens = mutableListOf<Pair<IntRange, TokenType>>()
        var idx = 0
        val len = lineText.length

        while (idx < len) {
            val char = lineText[idx]
            when {
                char == '{' || char == '}' || char == '[' || char == ']' || char == ':' || char == ',' -> {
                    tokens.add(IntRange(idx, idx) to TokenType.PUNCTUATION)
                    idx++
                }
                char == '"' -> {
                    val endQuote = findMatchingQuote(lineText, idx + 1)
                    if (endQuote != -1) {
                        val isKey = isFollowedByColon(lineText, endQuote + 1)
                        val type = if (isKey) TokenType.PROPERTY_KEY else TokenType.STRING_VALUE
                        tokens.add(IntRange(idx, endQuote) to type)
                        idx = endQuote + 1
                    } else {
                        tokens.add(IntRange(idx, len - 1) to TokenType.STRING_VALUE)
                        idx = len
                    }
                }
                char.isDigit() || char == '-' -> {
                    val numEnd = findNumberEnd(lineText, idx)
                    tokens.add(IntRange(idx, numEnd) to TokenType.NUMBER_VALUE)
                    idx = numEnd + 1
                }
                char == 't' || char == 'f' || char == 'n' -> {
                    val wordEnd = findKeywordEnd(lineText, idx)
                    val word = lineText.substring(idx, wordEnd + 1)
                    if (word == "true" || word == "false" || word == "null") {
                        tokens.add(IntRange(idx, wordEnd) to TokenType.KEYWORD_VALUE)
                        idx = wordEnd + 1
                    } else {
                        tokens.add(IntRange(idx, wordEnd) to TokenType.PLAIN_TEXT)
                        idx = wordEnd + 1
                    }
                }
                else -> {
                    idx++
                }
            }
        }

        return TokenMaker.buildAnnotatedStringFromTokens(tokens, lineText)
    }

    private fun findMatchingQuote(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            if (text[i] == '"' && text[i - 1] != '\\') return i
            i++
        }
        return -1
    }

    private fun isFollowedByColon(text: String, start: Int): Boolean {
        var i = start
        while (i < text.length) {
            if (text[i] == ':') return true
            if (!text[i].isWhitespace()) return false
            i++
        }
        return false
    }

    private fun findNumberEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (!c.isDigit() && c != '.' && c != 'e' && c != 'E' && c != '+' && c != '-') return i - 1
            i++
        }
        return text.length - 1
    }

    private fun findKeywordEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && text[i].isLetter()) {
            i++
        }
        return (i - 1).coerceAtLeast(start)
    }
}
