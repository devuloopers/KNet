package com.devuloopers.knet.ui.desktop.codeeditor.syntax.highlighter

import androidx.compose.ui.text.AnnotatedString
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.registry.CodeHighlighterRegistry

internal object CodeSyntaxHighlighter {

    fun highlightDocument(text: String, languageHint: String? = null): AnnotatedString {
        val highlighter = CodeHighlighterRegistry.resolveByLanguage(languageHint ?: "plain")
        val lines = text.lines()
        val builder = AnnotatedString.Builder()

        for (i in lines.indices) {
            builder.append(highlighter.highlightLine(lines[i]))
            if (i < lines.lastIndex) {
                builder.append("\n")
            }
        }
        return builder.toAnnotatedString()
    }
}
