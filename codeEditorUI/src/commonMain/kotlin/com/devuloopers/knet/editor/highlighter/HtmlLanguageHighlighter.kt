package com.devuloopers.knet.editor.highlighter

/**
 * Syntax highlighter strategy for HTML and XML documents.
 */
class HtmlLanguageHighlighter(
    override val languageId: String = "html"
) : CodeLanguageHighlighter {

    private val voidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        return TagMarkupHighlighter.calculateFoldRanges(lines, voidTags)
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
