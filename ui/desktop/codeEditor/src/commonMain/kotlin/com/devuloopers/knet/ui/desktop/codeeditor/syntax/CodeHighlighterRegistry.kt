package com.devuloopers.knet.ui.desktop.codeeditor.syntax

import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/**
 * Strategy Registry resolving the appropriate [CodeLanguageHighlighter]
 * strategy for a given [CodeLanguage] or string language identifier.
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
     * Resolves the matching [CodeLanguageHighlighter] strategy based on [CodeLanguage].
     *
     * @param language Strongly-typed [CodeLanguage] token.
     * @return Corresponding [CodeLanguageHighlighter] syntax highlighting strategy.
     */
    fun resolve(language: CodeLanguage): CodeLanguageHighlighter {
        return when (language) {
            CodeLanguage.JSON -> jsonHighlighter
            CodeLanguage.GRAPHQL -> graphQlHighlighter
            CodeLanguage.XML -> xmlHighlighter
            CodeLanguage.HTML -> htmlHighlighter
            CodeLanguage.JAVASCRIPT -> jsHighlighter
            CodeLanguage.CSS -> cssHighlighter
            CodeLanguage.PLAIN -> plainTextHighlighter
        }
    }

    /**
     * Resolves strategy by language ID string hint (e.g. "json", "html", "xml", "graphql", "plain").
     *
     * @param languageId String language hint.
     * @return Corresponding [CodeLanguageHighlighter] syntax highlighting strategy.
     */
    fun resolveByLanguage(languageId: String): CodeLanguageHighlighter {
        return resolve(CodeLanguage.fromId(languageId))
    }
}
