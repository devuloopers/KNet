package com.devuloopers.knet.ui.desktop.codeeditor.syntax.language

import androidx.compose.ui.text.AnnotatedString

internal class CssLanguageHighlighter : CodeLanguageHighlighter {

    override val languageId: String = "css"

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

    override fun highlightLine(lineText: String): AnnotatedString = AnnotatedString(lineText)
}
