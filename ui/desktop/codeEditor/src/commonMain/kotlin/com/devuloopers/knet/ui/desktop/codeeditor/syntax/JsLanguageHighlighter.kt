package com.devuloopers.knet.ui.desktop.codeeditor.syntax

/**
 * Syntax highlighter strategy for JavaScript code.
 */
class JsLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "javascript"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val foldRanges = mutableMapOf<Int, Int>()
        val stack = ArrayDeque<Int>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.endsWith("{") || trimmed.endsWith("[")) {
                stack.addLast(index)
            } else if (trimmed.startsWith("}") || trimmed.startsWith("]")) {
                if (stack.isNotEmpty()) {
                    val start = stack.removeLast()
                    foldRanges[start] = index
                }
            }
        }
        return foldRanges
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex < 0 || endLineIndex >= lines.size) return ""
        val endLineTrimmed = lines[endLineIndex].trim()
        return when {
            endLineTrimmed.startsWith("}") -> "} "
            endLineTrimmed.startsWith("]") -> "] "
            else -> ""
        }
    }

    override fun highlightLine(lineText: String): androidx.compose.ui.text.AnnotatedString {
        return TokenMaker.tokenizeLine(
            lineText,
            TokenState.NULL
        ).annotatedString
    }
}
