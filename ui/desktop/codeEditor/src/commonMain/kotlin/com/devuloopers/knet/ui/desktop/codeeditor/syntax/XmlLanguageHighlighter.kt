package com.devuloopers.knet.ui.desktop.codeeditor.syntax

/**
 * Syntax highlighter strategy tailored specifically for XML documents.
 * Handles XML prologs (<?xml ...?>), CDATA (<![CDATA[...]]>), XML comments, elements, attributes, and line folding.
 */
class XmlLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "xml"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        return TagMarkupHighlighter.calculateFoldRanges(lines)
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex < 0 || endLineIndex >= lines.size) return ""
        val endLineTrimmed = lines[endLineIndex].trim()
        return if (endLineTrimmed.startsWith("</")) "$endLineTrimmed " else ""
    }

    override fun highlightLine(lineText: String): androidx.compose.ui.text.AnnotatedString {
        return TokenMaker.tokenizeLine(
            lineText,
            TokenState.NULL
        ).annotatedString
    }
}
