package com.devuloopers.knet.ui.desktop.codeeditor.syntax

import com.devuloopers.knet.engine.formatter.model.BodyFormat

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
    private val graphQlHighlighter = GraphQlLanguageHighlighter()
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
            is BodyFormat.GraphQL -> graphQlHighlighter
            else -> plainTextHighlighter
        }
    }

    /**
     * Resolves the matching [CodeLanguageHighlighter] strategy based on [CodeLanguage].
     */
    fun resolve(language: com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage): CodeLanguageHighlighter {
        return when (language) {
            com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.JSON -> jsonHighlighter
            com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.GRAPHQL -> graphQlHighlighter
            com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.XML -> xmlHighlighter
            com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.HTML -> htmlHighlighter
            com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.JAVASCRIPT -> jsHighlighter
            com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.CSS -> cssHighlighter
            com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.PLAIN -> plainTextHighlighter
        }
    }

    /**
     * Resolves strategy by language ID string hint (e.g. "json", "html", "xml", "graphql", "plain").
     */
    fun resolveByLanguage(languageId: String): CodeLanguageHighlighter {
        return resolve(com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage.fromId(languageId))
    }
}
