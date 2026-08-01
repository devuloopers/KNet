package com.devuloopers.knet.ui.desktop.codeeditor.syntax.language

import androidx.compose.ui.text.AnnotatedString

internal class PlainTextLanguageHighlighter : CodeLanguageHighlighter {

    override val languageId: String = "plain"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> = emptyMap()

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String = ""

    override fun highlightLine(lineText: String): AnnotatedString = AnnotatedString(lineText)
}
