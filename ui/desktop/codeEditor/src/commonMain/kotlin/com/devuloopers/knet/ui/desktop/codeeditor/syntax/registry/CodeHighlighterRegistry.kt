package com.devuloopers.knet.ui.desktop.codeeditor.syntax.registry

import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.CodeLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.CssLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.HtmlLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.JsLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.JsonLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.PlainTextLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.XmlLanguageHighlighter

/**
 * Registry resolving the appropriate [CodeLanguageHighlighter] strategy by language ID string hint.
 */
internal object CodeHighlighterRegistry {
    private val jsonHighlighter = JsonLanguageHighlighter()
    private val htmlHighlighter = HtmlLanguageHighlighter()
    private val xmlHighlighter = XmlLanguageHighlighter()
    private val jsHighlighter = JsLanguageHighlighter()
    private val cssHighlighter = CssLanguageHighlighter()
    private val plainTextHighlighter = PlainTextLanguageHighlighter()

    /**
     * Resolves strategy by language ID string hint (e.g. "json", "html", "xml", "js", "css", "plain").
     */
    fun resolveByLanguage(languageId: String?): CodeLanguageHighlighter {
        if (languageId.isNullOrBlank()) return plainTextHighlighter
        val lang = languageId.trim().lowercase()
        return when (lang) {
            "json", "cbor", "grpc" -> jsonHighlighter
            "html" -> htmlHighlighter
            "xml" -> xmlHighlighter
            "javascript", "js" -> jsHighlighter
            "css" -> cssHighlighter
            else -> plainTextHighlighter
        }
    }
}
