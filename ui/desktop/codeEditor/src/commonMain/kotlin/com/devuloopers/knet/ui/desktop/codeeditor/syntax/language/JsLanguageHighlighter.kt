package com.devuloopers.knet.ui.desktop.codeeditor.syntax.language

import androidx.compose.ui.text.AnnotatedString
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenMaker
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenType

internal class JsLanguageHighlighter : CodeLanguageHighlighter {

    override val languageId: String = "js"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val braceStack = mutableListOf<Int>()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("{")) braceStack.add(i)
            if (line.contains("}") && braceStack.isNotEmpty()) {
                val start = braceStack.removeAt(braceStack.lastIndex)
                if (i > start) result[start] = i
            }
        }
        return result
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String = "}"

    override fun highlightLine(lineText: String): AnnotatedString {
        if (lineText.isBlank()) return AnnotatedString(lineText)
        val tokens = mutableListOf<Pair<IntRange, TokenType>>()
        val keywords = setOf("function", "const", "let", "var", "if", "else", "return", "class", "import", "export")

        var idx = 0
        val len = lineText.length
        while (idx < len) {
            if (lineText[idx].isLetter()) {
                val start = idx
                while (idx < len && (lineText[idx].isLetterOrDigit() || lineText[idx] == '_')) idx++
                val word = lineText.substring(start, idx)
                val type = if (word in keywords) TokenType.KEYWORD_VALUE else TokenType.PLAIN_TEXT
                tokens.add(IntRange(start, idx - 1) to type)
            } else {
                idx++
            }
        }

        return TokenMaker.buildAnnotatedStringFromTokens(tokens, lineText)
    }
}
