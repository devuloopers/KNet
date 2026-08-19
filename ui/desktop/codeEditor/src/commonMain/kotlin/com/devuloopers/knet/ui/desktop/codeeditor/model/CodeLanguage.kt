package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * Strongly typed, extensible language identifier used across the editor public API.
 *
 * Standard languages retain convenient singleton values while [Custom] allows an independently
 * developed language contribution to cross module boundaries without modifying this hierarchy.
 */
sealed interface CodeLanguage {
    /** Canonical lowercase identifier used by registries and persisted editor preferences. */
    val id: String

    /** Human-readable label suitable for selectors and status presentation. */
    val displayName: String

    /** JavaScript Object Notation. */
    data object JSON : CodeLanguage {
        override val id: String = "json"
        override val displayName: String = "JSON"
    }

    /** GraphQL query, mutation, subscription, and schema documents. */
    data object GRAPHQL : CodeLanguage {
        override val id: String = "graphql"
        override val displayName: String = "GraphQL"
    }

    /** Extensible Markup Language. */
    data object XML : CodeLanguage {
        override val id: String = "xml"
        override val displayName: String = "XML"
    }

    /** HyperText Markup Language. */
    data object HTML : CodeLanguage {
        override val id: String = "html"
        override val displayName: String = "HTML"
    }

    /** JavaScript ECMAScript source code. */
    data object JAVASCRIPT : CodeLanguage {
        override val id: String = "javascript"
        override val displayName: String = "JavaScript"
    }

    /** Cascading Style Sheets. */
    data object CSS : CodeLanguage {
        override val id: String = "css"
        override val displayName: String = "CSS"
    }

    /** Plain unformatted text fallback. */
    data object PLAIN : CodeLanguage {
        override val id: String = "plain"
        override val displayName: String = "Plain Text"
    }

    /**
     * Language identifier contributed outside the built-in editor package.
     *
     * @property id Stable identifier containing lowercase letters, digits, dots, underscores, or hyphens.
     * @property displayName Human-readable language name.
     */
    data class Custom(
        override val id: String,
        override val displayName: String
    ) : CodeLanguage {
        init {
            require(id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) {
                "Custom code language identifier must be lowercase and contain only letters, digits, dots, underscores, or hyphens."
            }
            require(displayName.isNotBlank()) { "Custom code language display name must not be blank." }
        }
    }

    companion object {
        /** Ordered built-in languages available without external contributions. */
        val builtIns: List<CodeLanguage> = listOf(JSON, GRAPHQL, XML, HTML, JAVASCRIPT, CSS, PLAIN)

        /**
         * Resolves a built-in language or preserves an unknown identifier as [Custom].
         *
         * Blank identifiers resolve to [PLAIN]. Unlike the former enum contract, a valid unknown
         * identifier is not silently discarded, allowing a separately registered language to resolve.
         *
         * @param id Canonical identifier or supported alias.
         * @param customDisplayName Optional display name for an unknown identifier.
         * @return Built-in, custom, or plain language identifier.
         */
        fun fromId(id: String?, customDisplayName: String? = null): CodeLanguage {
            val normalized = id?.trim()?.lowercase().orEmpty()
            return when (normalized) {
                "" -> PLAIN
                "json" -> JSON
                "graphql", "gql" -> GRAPHQL
                "xml" -> XML
                "html" -> HTML
                "javascript", "js", "ecmascript" -> JAVASCRIPT
                "css" -> CSS
                "plain", "text", "plaintext" -> PLAIN
                else -> Custom(normalized, customDisplayName?.takeIf(String::isNotBlank) ?: normalized)
            }
        }
    }
}
