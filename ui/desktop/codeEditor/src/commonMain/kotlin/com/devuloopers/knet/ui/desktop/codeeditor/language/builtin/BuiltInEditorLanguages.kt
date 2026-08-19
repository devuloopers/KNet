package com.devuloopers.knet.ui.desktop.codeeditor.language.builtin

import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorBracketPair
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorCommentConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorLanguageRegistry
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorLanguageSupport
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/**
 * Built-in language contributions shipped with the reusable editor foundation.
 */
object BuiltInEditorLanguages {
    private val curlySquarePairs = setOf(
        EditorBracketPair('{', '}'),
        EditorBracketPair('[', ']')
    )
    private val scriptPairs = curlySquarePairs + EditorBracketPair('(', ')')

    private val jsonTokenizer = CStyleSyntaxTokenizer(
        CStyleTokenizerConfiguration(
            recognizeQuotedProperties = true,
            quoteCharacters = setOf('"')
        )
    )
    private val graphQlTokenizer = GraphQlSyntaxTokenizer()
    private val javaScriptTokenizer = CStyleSyntaxTokenizer(
        CStyleTokenizerConfiguration(
            keywords = setOf(
                "async", "await", "break", "case", "catch", "class", "const", "continue",
                "default", "delete", "do", "else", "export", "extends", "finally", "for",
                "function", "if", "import", "in", "instanceof", "let", "new", "return",
                "switch", "throw", "try", "typeof", "var", "while", "yield"
            ),
            booleanLiterals = setOf("true", "false", "null", "undefined"),
            lineCommentPrefixes = setOf("//"),
            supportsBlockComments = true,
            quoteCharacters = setOf('"', '\'', '`'),
            multilineQuoteCharacters = setOf('`')
        )
    )
    private val cssTokenizer = CStyleSyntaxTokenizer(
        CStyleTokenizerConfiguration(
            supportsBlockComments = true,
            quoteCharacters = setOf('"', '\''),
            separators = setOf('{', '}', '(', ')', ':', ';', ',')
        )
    )
    private val markupTokenizer = MarkupSyntaxTokenizer()

    /** Ordered support definitions used by [registry]. */
    val supports: List<EditorLanguageSupport> = listOf(
        EditorLanguageSupport(
            language = CodeLanguage.JSON,
            aliases = setOf("jsonc"),
            mimeTypes = setOf("application/json", "application/problem+json"),
            tokenizer = jsonTokenizer,
            foldingProvider = TokenAwareBracketFoldingProvider(jsonTokenizer, curlySquarePairs),
            indentationProvider = BracketIndentationProvider(setOf('{', '['), setOf('}', ']')),
            bracketProvider = StandardBracketProvider(curlySquarePairs)
        ),
        EditorLanguageSupport(
            language = CodeLanguage.GRAPHQL,
            aliases = setOf("gql"),
            mimeTypes = setOf("application/graphql", "application/graphql-response+json"),
            tokenizer = graphQlTokenizer,
            foldingProvider = TokenAwareBracketFoldingProvider(graphQlTokenizer, scriptPairs),
            indentationProvider = BracketIndentationProvider(setOf('{', '(', '['), setOf('}', ')', ']')),
            bracketProvider = StandardBracketProvider(scriptPairs),
            comments = EditorCommentConfiguration(linePrefix = "#")
        ),
        EditorLanguageSupport(
            language = CodeLanguage.XML,
            aliases = setOf("svg"),
            mimeTypes = setOf("application/xml", "text/xml", "image/svg+xml"),
            tokenizer = markupTokenizer,
            foldingProvider = MarkupFoldingProvider(),
            comments = EditorCommentConfiguration(blockStart = "<!--", blockEnd = "-->")
        ),
        EditorLanguageSupport(
            language = CodeLanguage.HTML,
            mimeTypes = setOf("text/html"),
            tokenizer = markupTokenizer,
            foldingProvider = MarkupFoldingProvider(
                setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
            ),
            comments = EditorCommentConfiguration(blockStart = "<!--", blockEnd = "-->")
        ),
        EditorLanguageSupport(
            language = CodeLanguage.JAVASCRIPT,
            aliases = setOf("js", "ecmascript"),
            mimeTypes = setOf("text/javascript", "application/javascript"),
            tokenizer = javaScriptTokenizer,
            foldingProvider = TokenAwareBracketFoldingProvider(javaScriptTokenizer, scriptPairs),
            indentationProvider = BracketIndentationProvider(setOf('{', '(', '['), setOf('}', ')', ']')),
            bracketProvider = StandardBracketProvider(scriptPairs),
            comments = EditorCommentConfiguration(linePrefix = "//", blockStart = "/*", blockEnd = "*/")
        ),
        EditorLanguageSupport(
            language = CodeLanguage.CSS,
            mimeTypes = setOf("text/css"),
            tokenizer = cssTokenizer,
            foldingProvider = TokenAwareBracketFoldingProvider(cssTokenizer, setOf(EditorBracketPair('{', '}'))),
            indentationProvider = BracketIndentationProvider(setOf('{'), setOf('}')),
            bracketProvider = StandardBracketProvider(setOf(EditorBracketPair('{', '}'))),
            comments = EditorCommentConfiguration(blockStart = "/*", blockEnd = "*/")
        ),
        EditorLanguageSupport(language = CodeLanguage.PLAIN, aliases = setOf("text", "plaintext"), mimeTypes = setOf("text/plain"))
    )

    /** Immutable default registry containing [supports]. */
    val registry: EditorLanguageRegistry = EditorLanguageRegistry(supports)
}
