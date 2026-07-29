package com.devuloopers.knet.editor.highlighter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Strategy Registry resolving the appropriate [CodeLanguageHighlighter]
 * strategy for a given payload or format.
 */
object CodeHighlighterRegistry {
    private val jsonHighlighter = JsonLanguageHighlighter()
    private val htmlHighlighter = HtmlLanguageHighlighter("html")
    private val xmlHighlighter = XmlLanguageHighlighter()
    private val jsHighlighter = JsLanguageHighlighter()
    private val cssHighlighter = CssLanguageHighlighter()
    private val plainTextHighlighter = PlainTextLanguageHighlighter()

    /**
     * Resolves the matching [CodeLanguageHighlighter] strategy based on [BodyFormat].
     */
    fun resolve(bodyFormat: BodyFormat?): CodeLanguageHighlighter {
        return when (bodyFormat) {
            is BodyFormat.Json, is BodyFormat.JsonStream, is BodyFormat.Cbor, is BodyFormat.GrpcWeb -> jsonHighlighter
            is BodyFormat.Html -> htmlHighlighter
            is BodyFormat.Xml -> xmlHighlighter
            is BodyFormat.Js -> jsHighlighter
            is BodyFormat.Css -> cssHighlighter
            else -> plainTextHighlighter
        }
    }

    /**
     * Resolves strategy by language ID string hint (e.g. "json", "html", "xml", "plain").
     */
    fun resolveByLanguage(languageId: String): CodeLanguageHighlighter {
        return when (languageId.trim().lowercase()) {
            "json" -> jsonHighlighter
            "html" -> htmlHighlighter
            "xml" -> xmlHighlighter
            "javascript", "js" -> jsHighlighter
            "css" -> cssHighlighter
            else -> plainTextHighlighter
        }
    }
}
