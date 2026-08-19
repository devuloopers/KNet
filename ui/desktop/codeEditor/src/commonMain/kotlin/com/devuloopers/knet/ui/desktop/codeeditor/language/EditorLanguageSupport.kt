package com.devuloopers.knet.ui.desktop.codeeditor.language

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.concurrency.EditorCancellationCheckpoint
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/** Semantic category assigned to a token independently from a rendering theme. */
sealed interface EditorTokenCategory {
    /** Standard semantic token categories understood by the built-in renderer. */
    enum class Standard : EditorTokenCategory {
        Keyword,
        String,
        Number,
        Boolean,
        Comment,
        Identifier,
        Property,
        Separator,
        Tag,
        Attribute,
        Directive,
        Variable,
        Type,
        Declaration
    }

    /**
     * Token category contributed by an extension with a custom theme mapping.
     *
     * @property id Namespaced stable category identifier.
     */
    data class Custom(val id: String) : EditorTokenCategory {
        init {
            require(id.isNotBlank() && id.none(Char::isWhitespace)) {
                "Custom token category identifier must be non-blank and contain no whitespace."
            }
        }
    }
}

/**
 * Immutable semantic token within one logical line.
 *
 * @property startOffset Inclusive UTF-16 line offset.
 * @property endOffset Exclusive UTF-16 line offset.
 * @property category Semantic category interpreted by the active editor theme.
 */
data class EditorToken(
    val startOffset: Int,
    val endOffset: Int,
    val category: EditorTokenCategory
) {
    init {
        require(startOffset >= 0) { "Token start offset must be non-negative." }
        require(endOffset >= startOffset) { "Token end offset must not precede its start." }
    }
}

/**
 * Opaque lexical state propagated between adjacent logical lines.
 *
 * Implementations should use immutable value objects with structural equality so incremental
 * tokenization can detect when state converges with a previous document version.
 */
interface EditorLexicalState

/** Initial lexical state used by stateless and plain-text tokenizers. */
data object InitialEditorLexicalState : EditorLexicalState

/**
 * Result of tokenizing one logical line.
 *
 * @property startState State received from the preceding line.
 * @property endState State to pass to the following line.
 * @property tokens Ordered semantic tokens within the line.
 */
data class EditorTokenizedLine(
    val startState: EditorLexicalState,
    val endState: EditorLexicalState,
    val tokens: List<EditorToken>
)

/** Stateful syntax tokenizer for one editor language. */
interface EditorSyntaxTokenizer {
    /** Initial state used for the first line of a document. */
    val initialState: EditorLexicalState

    /**
     * Tokenizes one line and produces state for the next line.
     *
     * @param lineText Logical line content without a newline.
     * @param startState Lexical state produced by the previous line.
     * @return Semantic tokens and next-line state.
     */
    fun tokenizeLine(lineText: String, startState: EditorLexicalState): EditorTokenizedLine
}

/** Optional language capability that calculates collapsible document regions. */
fun interface EditorFoldingProvider {
    /**
     * Calculates fold regions for an immutable snapshot.
     *
     * @param snapshot Snapshot to inspect.
     * @param checkpoint Cooperative cancellation checkpoint for long documents.
     * @return Ordered fold regions in logical document coordinates.
     */
    fun calculate(
        snapshot: EditorDocumentSnapshot,
        checkpoint: EditorCancellationCheckpoint
    ): List<FoldRegion>
}

/** Optional language capability that determines indentation after a line break. */
fun interface EditorIndentationProvider {
    /**
     * Calculates indentation for a new line inserted at [position].
     *
     * @param snapshot Current document snapshot.
     * @param position Line-break position.
     * @return Whitespace prefix for the new line.
     */
    fun indentationForNewLine(snapshot: EditorDocumentSnapshot, position: EditorPosition): String
}

/**
 * One opening and closing delimiter pair recognized by a language.
 *
 * @property opening Opening delimiter.
 * @property closing Closing delimiter.
 */
data class EditorBracketPair(val opening: Char, val closing: Char)

/** Optional language bracket capability. */
interface EditorBracketProvider {
    /** Bracket pairs recognized by the language. */
    val pairs: Set<EditorBracketPair>
}

/**
 * Optional line and block comment delimiters for editor commands.
 *
 * @property linePrefix Prefix for line comments, or `null` when unsupported.
 * @property blockStart Opening block-comment delimiter, or `null` when unsupported.
 * @property blockEnd Closing block-comment delimiter, or `null` when unsupported.
 */
data class EditorCommentConfiguration(
    val linePrefix: String? = null,
    val blockStart: String? = null,
    val blockEnd: String? = null
) {
    init {
        require((blockStart == null) == (blockEnd == null)) {
            "Block comment start and end delimiters must be supplied together."
        }
    }
}

/**
 * Independently registrable capabilities for one editor language.
 *
 * All capabilities are optional. A language may begin as plain colored text and later add folding,
 * indentation, bracket, or comment behavior without changing the editor core.
 *
 * @property language Strongly typed language identifier.
 * @property aliases Additional identifiers accepted by the registry.
 * @property mimeTypes MIME types associated with the language.
 * @property tokenizer Optional stateful syntax tokenizer.
 * @property foldingProvider Optional folding capability.
 * @property indentationProvider Optional indentation capability.
 * @property bracketProvider Optional bracket capability.
 * @property comments Optional comment delimiters.
 */
data class EditorLanguageSupport(
    val language: CodeLanguage,
    val aliases: Set<String> = emptySet(),
    val mimeTypes: Set<String> = emptySet(),
    val tokenizer: EditorSyntaxTokenizer? = null,
    val foldingProvider: EditorFoldingProvider? = null,
    val indentationProvider: EditorIndentationProvider? = null,
    val bracketProvider: EditorBracketProvider? = null,
    val comments: EditorCommentConfiguration? = null
)

/**
 * Immutable registry of editor-language contributions.
 *
 * @param contributions Language support definitions. Canonical identifiers and aliases must be unique.
 */
class EditorLanguageRegistry(contributions: Iterable<EditorLanguageSupport>) {
    private val supports: List<EditorLanguageSupport> = contributions.toList()
    private val byIdentifier: Map<String, EditorLanguageSupport> = buildMap {
        for (support in supports) {
            register(support.language.id, support)
            support.aliases.forEach { register(it, support) }
        }
    }
    private val byMimeType: Map<String, EditorLanguageSupport> = buildMap {
        for (support in supports) {
            for (mimeType in support.mimeTypes) {
                val normalized = mimeType.normalizedLanguageKey()
                require(put(normalized, support) == null) { "Duplicate editor MIME type '$mimeType'." }
            }
        }
    }

    /** All registered support definitions in contribution order. */
    val languages: List<EditorLanguageSupport>
        get() = supports

    /**
     * Resolves support by strongly typed language, falling back to registered plain text.
     *
     * @param language Requested language.
     * @return Matching support or plain-text support.
     * @throws IllegalStateException when neither the requested nor plain language is registered.
     */
    fun resolve(language: CodeLanguage): EditorLanguageSupport {
        return byIdentifier[language.id.normalizedLanguageKey()]
            ?: byIdentifier[CodeLanguage.PLAIN.id]
            ?: error("Editor registry must contain plain-text language support.")
    }

    /**
     * Resolves a canonical identifier or alias.
     *
     * @param identifier Identifier or alias.
     * @return Matching support, or `null` when absent.
     */
    fun find(identifier: String): EditorLanguageSupport? = byIdentifier[identifier.normalizedLanguageKey()]

    /**
     * Resolves an exact MIME type, ignoring parameters such as a charset.
     *
     * @param mimeType MIME type with optional parameters.
     * @return Matching support, or `null` when absent.
     */
    fun findByMimeType(mimeType: String): EditorLanguageSupport? {
        return byMimeType[mimeType.substringBefore(';').normalizedLanguageKey()]
    }

    /**
     * Returns a new registry containing existing and additional contributions.
     *
     * @param additional Contributions to append.
     * @return New validated immutable registry.
     */
    fun with(additional: Iterable<EditorLanguageSupport>): EditorLanguageRegistry {
        return EditorLanguageRegistry(supports + additional)
    }

    private fun MutableMap<String, EditorLanguageSupport>.register(
        identifier: String,
        support: EditorLanguageSupport
    ) {
        val normalized = identifier.normalizedLanguageKey()
        require(normalized.isNotEmpty()) { "Editor language identifier must not be blank." }
        require(put(normalized, support) == null) { "Duplicate editor language identifier or alias '$identifier'." }
    }
}

private fun String.normalizedLanguageKey(): String = trim().lowercase()
