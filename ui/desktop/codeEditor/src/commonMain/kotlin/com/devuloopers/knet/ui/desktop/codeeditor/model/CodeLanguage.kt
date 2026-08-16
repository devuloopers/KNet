package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * Strongly-typed enumeration of programming and document languages supported by the KNet Code Editor.
 *
 * Replaces untyped primitive string language hints across the desktop codebase to ensure
 * compile-time safety and prevent typo-based highlighting failures.
 *
 * @property id Canonical language identifier string token.
 * @property displayName Human-readable display label suitable for IDE status bars and selectors.
 */
public enum class CodeLanguage(
    public val id: String,
    public val displayName: String
) {
    /** JavaScript Object Notation. */
    JSON("json", "JSON"),

    /** GraphQL query and mutation language. */
    GRAPHQL("graphql", "GraphQL"),

    /** Extensible Markup Language. */
    XML("xml", "XML"),

    /** HyperText Markup Language. */
    HTML("html", "HTML"),

    /** JavaScript ECMAScript source code. */
    JAVASCRIPT("javascript", "JavaScript"),

    /** Cascading Style Sheets. */
    CSS("css", "CSS"),

    /** Plain unformatted text fallback. */
    PLAIN("plain", "Plain Text");

    public companion object {
        /**
         * Resolves a [CodeLanguage] from a raw string identifier, falling back to [PLAIN] if unrecognized.
         *
         * @param id String identifier (e.g. "json", "graphql", "js", "html", "xml").
         * @return Resolved [CodeLanguage].
         */
        public fun fromId(id: String?): CodeLanguage {
            if (id.isNullOrBlank()) return PLAIN
            return when (id.trim().lowercase()) {
                "json" -> JSON
                "graphql", "gql" -> GRAPHQL
                "xml" -> XML
                "html" -> HTML
                "javascript", "js" -> JAVASCRIPT
                "css" -> CSS
                else -> PLAIN
            }
        }
    }
}
