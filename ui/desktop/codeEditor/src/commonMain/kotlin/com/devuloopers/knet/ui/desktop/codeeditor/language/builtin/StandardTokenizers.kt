package com.devuloopers.knet.ui.desktop.codeeditor.language.builtin

import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorLexicalState
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorSyntaxTokenizer
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorToken
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenCategory
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenizedLine
import com.devuloopers.knet.ui.desktop.codeeditor.language.InitialEditorLexicalState

internal data class CStyleTokenizerConfiguration(
    val keywords: Set<String> = emptySet(),
    val booleanLiterals: Set<String> = setOf("true", "false", "null"),
    val lineCommentPrefixes: Set<String> = emptySet(),
    val supportsBlockComments: Boolean = false,
    val quoteCharacters: Set<Char> = setOf('"'),
    val multilineQuoteCharacters: Set<Char> = emptySet(),
    val recognizeQuotedProperties: Boolean = false,
    val separators: Set<Char> = setOf('{', '}', '[', ']', '(', ')', ':', ',', ';')
)

private sealed interface CStyleLexicalState : EditorLexicalState {
    data object Normal : CStyleLexicalState
    data object BlockComment : CStyleLexicalState
    data class Quoted(val delimiter: Char) : CStyleLexicalState
}

internal class CStyleSyntaxTokenizer(
    private val configuration: CStyleTokenizerConfiguration
) : EditorSyntaxTokenizer {
    override val initialState: EditorLexicalState = CStyleLexicalState.Normal

    override fun tokenizeLine(lineText: String, startState: EditorLexicalState): EditorTokenizedLine {
        var state = startState as? CStyleLexicalState ?: CStyleLexicalState.Normal
        val tokens = mutableListOf<EditorToken>()
        var offset = 0

        while (offset < lineText.length) {
            when (val activeState = state) {
                CStyleLexicalState.BlockComment -> {
                    val closing = lineText.indexOf("*/", offset)
                    if (closing < 0) {
                        tokens += token(offset, lineText.length, EditorTokenCategory.Standard.Comment)
                        offset = lineText.length
                    } else {
                        tokens += token(offset, closing + 2, EditorTokenCategory.Standard.Comment)
                        offset = closing + 2
                        state = CStyleLexicalState.Normal
                    }
                }
                is CStyleLexicalState.Quoted -> {
                    val end = findQuotedEnd(lineText, offset, activeState.delimiter, openingDelimiterIncluded = false)
                    if (end < 0) {
                        tokens += token(offset, lineText.length, EditorTokenCategory.Standard.String)
                        offset = lineText.length
                        if (activeState.delimiter !in configuration.multilineQuoteCharacters) {
                            state = CStyleLexicalState.Normal
                        }
                    } else {
                        tokens += token(offset, end, EditorTokenCategory.Standard.String)
                        offset = end
                        state = CStyleLexicalState.Normal
                    }
                }
                CStyleLexicalState.Normal -> {
                    val lineComment = configuration.lineCommentPrefixes.firstOrNull { prefix ->
                        lineText.startsWith(prefix, offset)
                    }
                    when {
                        lineComment != null -> {
                            tokens += token(offset, lineText.length, EditorTokenCategory.Standard.Comment)
                            offset = lineText.length
                        }
                        configuration.supportsBlockComments && lineText.startsWith("/*", offset) -> {
                            val closing = lineText.indexOf("*/", offset + 2)
                            if (closing < 0) {
                                tokens += token(offset, lineText.length, EditorTokenCategory.Standard.Comment)
                                offset = lineText.length
                                state = CStyleLexicalState.BlockComment
                            } else {
                                tokens += token(offset, closing + 2, EditorTokenCategory.Standard.Comment)
                                offset = closing + 2
                            }
                        }
                        lineText[offset] in configuration.quoteCharacters -> {
                            val delimiter = lineText[offset]
                            val end = findQuotedEnd(lineText, offset, delimiter, openingDelimiterIncluded = true)
                            val tokenEnd = if (end < 0) lineText.length else end
                            val category = if (
                                configuration.recognizeQuotedProperties &&
                                delimiter == '"' &&
                                isFollowedByColon(lineText, tokenEnd)
                            ) {
                                EditorTokenCategory.Standard.Property
                            } else {
                                EditorTokenCategory.Standard.String
                            }
                            tokens += token(offset, tokenEnd, category)
                            offset = tokenEnd
                            if (end < 0 && delimiter in configuration.multilineQuoteCharacters) {
                                state = CStyleLexicalState.Quoted(delimiter)
                            }
                        }
                        lineText[offset].isDigit() || isSignedNumberStart(lineText, offset) -> {
                            val end = scanNumber(lineText, offset)
                            tokens += token(offset, end, EditorTokenCategory.Standard.Number)
                            offset = end
                        }
                        lineText[offset].isLetter() || lineText[offset] == '_' || lineText[offset] == '$' -> {
                            val end = scanIdentifier(lineText, offset)
                            val value = lineText.substring(offset, end)
                            val category = when (value) {
                                in configuration.keywords -> EditorTokenCategory.Standard.Keyword
                                in configuration.booleanLiterals -> EditorTokenCategory.Standard.Boolean
                                else -> EditorTokenCategory.Standard.Identifier
                            }
                            tokens += token(offset, end, category)
                            offset = end
                        }
                        lineText[offset] in configuration.separators -> {
                            tokens += token(offset, offset + 1, EditorTokenCategory.Standard.Separator)
                            offset++
                        }
                        else -> offset++
                    }
                }
            }
        }

        return EditorTokenizedLine(startState, state, tokens)
    }

    private fun findQuotedEnd(
        text: String,
        start: Int,
        delimiter: Char,
        openingDelimiterIncluded: Boolean
    ): Int {
        var index = if (openingDelimiterIncluded) start + 1 else start
        while (index < text.length) {
            when {
                text[index] == '\\' -> index += 2
                text[index] == delimiter -> return index + 1
                else -> index++
            }
        }
        return -1
    }

    private fun isFollowedByColon(text: String, tokenEnd: Int): Boolean {
        var offset = tokenEnd
        while (offset < text.length && text[offset].isWhitespace()) offset++
        return offset < text.length && text[offset] == ':'
    }

    private fun isSignedNumberStart(text: String, offset: Int): Boolean {
        return text[offset] == '-' && offset + 1 < text.length && text[offset + 1].isDigit()
    }

    private fun scanNumber(text: String, start: Int): Int {
        var offset = start
        if (text[offset] == '-') offset++
        while (offset < text.length && (text[offset].isDigit() || text[offset] in ".eE+-")) offset++
        return offset
    }

    private fun scanIdentifier(text: String, start: Int): Int {
        var offset = start + 1
        while (offset < text.length && (text[offset].isLetterOrDigit() || text[offset] in "_$-")) offset++
        return offset
    }
}

private sealed interface GraphQlLexicalState : EditorLexicalState {
    data object Normal : GraphQlLexicalState
    data object BlockString : GraphQlLexicalState
}

internal class GraphQlSyntaxTokenizer : EditorSyntaxTokenizer {
    override val initialState: EditorLexicalState = GraphQlLexicalState.Normal

    private val keywords = setOf(
        "query", "mutation", "subscription", "fragment", "on", "type", "schema", "extend",
        "input", "enum", "interface", "union", "scalar", "implements", "directive"
    )
    private val builtInTypes = setOf("ID", "String", "Int", "Boolean", "Float")
    private val literals = setOf("true", "false", "null")

    override fun tokenizeLine(lineText: String, startState: EditorLexicalState): EditorTokenizedLine {
        var state = startState as? GraphQlLexicalState ?: GraphQlLexicalState.Normal
        val tokens = mutableListOf<EditorToken>()
        var offset = 0
        while (offset < lineText.length) {
            if (state == GraphQlLexicalState.BlockString) {
                val closing = lineText.indexOf("\"\"\"", offset)
                if (closing < 0) {
                    tokens += token(offset, lineText.length, EditorTokenCategory.Standard.String)
                    offset = lineText.length
                    continue
                }
                tokens += token(offset, closing + 3, EditorTokenCategory.Standard.String)
                offset = closing + 3
                state = GraphQlLexicalState.Normal
                continue
            }

            when {
                lineText.startsWith("\"\"\"", offset) -> {
                    val closing = lineText.indexOf("\"\"\"", offset + 3)
                    if (closing < 0) {
                        tokens += token(offset, lineText.length, EditorTokenCategory.Standard.String)
                        state = GraphQlLexicalState.BlockString
                        offset = lineText.length
                    } else {
                        tokens += token(offset, closing + 3, EditorTokenCategory.Standard.String)
                        offset = closing + 3
                    }
                }
                lineText[offset] == '#' -> {
                    tokens += token(offset, lineText.length, EditorTokenCategory.Standard.Comment)
                    offset = lineText.length
                }
                lineText[offset] == '"' -> {
                    val end = scanGraphQlString(lineText, offset)
                    tokens += token(offset, end, EditorTokenCategory.Standard.String)
                    offset = end
                }
                lineText[offset] == '$' -> {
                    val end = scanGraphQlName(lineText, offset + 1)
                    tokens += token(offset, end, EditorTokenCategory.Standard.Variable)
                    offset = end
                }
                lineText[offset] == '@' -> {
                    val end = scanGraphQlName(lineText, offset + 1)
                    tokens += token(offset, end, EditorTokenCategory.Standard.Directive)
                    offset = end
                }
                lineText[offset].isDigit() || lineText[offset] == '-' -> {
                    val end = scanGraphQlNumber(lineText, offset)
                    tokens += token(offset, end, EditorTokenCategory.Standard.Number)
                    offset = end
                }
                lineText[offset].isLetter() || lineText[offset] == '_' -> {
                    val end = scanGraphQlName(lineText, offset)
                    val word = lineText.substring(offset, end)
                    val category = when (word) {
                        in keywords -> EditorTokenCategory.Standard.Keyword
                        in builtInTypes -> EditorTokenCategory.Standard.Type
                        in literals -> EditorTokenCategory.Standard.Boolean
                        else -> EditorTokenCategory.Standard.Identifier
                    }
                    tokens += token(offset, end, category)
                    offset = end
                }
                lineText[offset] in "{}[]():!,=" -> {
                    tokens += token(offset, offset + 1, EditorTokenCategory.Standard.Separator)
                    offset++
                }
                else -> offset++
            }
        }
        return EditorTokenizedLine(startState, state, tokens)
    }

    private fun scanGraphQlString(text: String, start: Int): Int {
        var offset = start + 1
        while (offset < text.length) {
            when {
                text[offset] == '\\' -> offset += 2
                text[offset] == '"' -> return offset + 1
                else -> offset++
            }
        }
        return text.length
    }

    private fun scanGraphQlName(text: String, start: Int): Int {
        var offset = start
        while (offset < text.length && (text[offset].isLetterOrDigit() || text[offset] == '_')) offset++
        return offset
    }

    private fun scanGraphQlNumber(text: String, start: Int): Int {
        var offset = start + 1
        while (offset < text.length && (text[offset].isDigit() || text[offset] in ".eE+-")) offset++
        return offset
    }
}

private sealed interface MarkupLexicalState : EditorLexicalState {
    data object Normal : MarkupLexicalState
    data object Comment : MarkupLexicalState
    data object Declaration : MarkupLexicalState
    data class Tag(val quote: Char? = null) : MarkupLexicalState
}

internal class MarkupSyntaxTokenizer : EditorSyntaxTokenizer {
    override val initialState: EditorLexicalState = MarkupLexicalState.Normal

    override fun tokenizeLine(lineText: String, startState: EditorLexicalState): EditorTokenizedLine {
        var state = startState as? MarkupLexicalState ?: MarkupLexicalState.Normal
        val tokens = mutableListOf<EditorToken>()
        var offset = 0
        while (offset < lineText.length) {
            when (val activeState = state) {
                MarkupLexicalState.Comment -> {
                    val end = lineText.indexOf("-->", offset)
                    if (end < 0) {
                        tokens += token(offset, lineText.length, EditorTokenCategory.Standard.Comment)
                        offset = lineText.length
                    } else {
                        tokens += token(offset, end + 3, EditorTokenCategory.Standard.Comment)
                        offset = end + 3
                        state = MarkupLexicalState.Normal
                    }
                }
                MarkupLexicalState.Declaration -> {
                    val end = lineText.indexOf('>', offset)
                    val tokenEnd = if (end < 0) lineText.length else end + 1
                    tokens += token(offset, tokenEnd, EditorTokenCategory.Standard.Declaration)
                    offset = tokenEnd
                    if (end >= 0) state = MarkupLexicalState.Normal
                }
                is MarkupLexicalState.Tag -> {
                    val tagResult = tokenizeTagRemainder(lineText, offset, activeState.quote, tokens)
                    offset = tagResult.endOffset
                    state = tagResult.endState
                }
                MarkupLexicalState.Normal -> when {
                    lineText.startsWith("<!--", offset) -> {
                        val end = lineText.indexOf("-->", offset + 4)
                        if (end < 0) {
                            tokens += token(offset, lineText.length, EditorTokenCategory.Standard.Comment)
                            state = MarkupLexicalState.Comment
                            offset = lineText.length
                        } else {
                            tokens += token(offset, end + 3, EditorTokenCategory.Standard.Comment)
                            offset = end + 3
                        }
                    }
                    lineText.startsWith("<!", offset) || lineText.startsWith("<?", offset) -> {
                        val end = lineText.indexOf('>', offset + 2)
                        val tokenEnd = if (end < 0) lineText.length else end + 1
                        tokens += token(offset, tokenEnd, EditorTokenCategory.Standard.Declaration)
                        offset = tokenEnd
                        if (end < 0) state = MarkupLexicalState.Declaration
                    }
                    lineText[offset] == '<' -> {
                        val tagResult = tokenizeTagRemainder(lineText, offset, quote = null, tokens)
                        offset = tagResult.endOffset
                        state = tagResult.endState
                    }
                    else -> offset++
                }
            }
        }
        return EditorTokenizedLine(startState, state, tokens)
    }

    private fun tokenizeTagRemainder(
        text: String,
        start: Int,
        quote: Char?,
        tokens: MutableList<EditorToken>
    ): MarkupTagResult {
        var offset = start
        var activeQuote = quote
        if (activeQuote != null) {
            val quoteEnd = findQuoteEnd(text, offset, activeQuote)
            val tokenEnd = if (quoteEnd < 0) text.length else quoteEnd
            tokens += token(offset, tokenEnd, EditorTokenCategory.Standard.String)
            if (quoteEnd < 0) return MarkupTagResult(text.length, MarkupLexicalState.Tag(activeQuote))
            offset = quoteEnd
            activeQuote = null
        }

        if (offset < text.length && text[offset] == '<') {
            tokens += token(offset, offset + 1, EditorTokenCategory.Standard.Separator)
            offset++
            if (offset < text.length && text[offset] == '/') {
                tokens += token(offset, offset + 1, EditorTokenCategory.Standard.Separator)
                offset++
            }
            val nameEnd = scanMarkupName(text, offset)
            if (nameEnd > offset) tokens += token(offset, nameEnd, EditorTokenCategory.Standard.Tag)
            offset = nameEnd
        }

        while (offset < text.length) {
            when {
                text[offset] == '>' -> {
                    tokens += token(offset, offset + 1, EditorTokenCategory.Standard.Separator)
                    return MarkupTagResult(offset + 1, MarkupLexicalState.Normal)
                }
                text[offset] == '/' && offset + 1 < text.length && text[offset + 1] == '>' -> {
                    tokens += token(offset, offset + 2, EditorTokenCategory.Standard.Separator)
                    return MarkupTagResult(offset + 2, MarkupLexicalState.Normal)
                }
                text[offset] == '"' || text[offset] == '\'' -> {
                    activeQuote = text[offset]
                    val quoteEnd = findQuoteEnd(text, offset + 1, activeQuote)
                    val tokenEnd = if (quoteEnd < 0) text.length else quoteEnd
                    tokens += token(offset, tokenEnd, EditorTokenCategory.Standard.String)
                    if (quoteEnd < 0) return MarkupTagResult(text.length, MarkupLexicalState.Tag(activeQuote))
                    offset = quoteEnd
                    activeQuote = null
                }
                text[offset].isLetter() || text[offset] == '_' || text[offset] == ':' -> {
                    val nameEnd = scanMarkupName(text, offset)
                    tokens += token(offset, nameEnd, EditorTokenCategory.Standard.Attribute)
                    offset = nameEnd
                }
                else -> offset++
            }
        }
        return MarkupTagResult(text.length, MarkupLexicalState.Tag(activeQuote))
    }

    private fun findQuoteEnd(text: String, start: Int, quote: Char): Int {
        var offset = start
        while (offset < text.length) {
            if (text[offset] == quote) return offset + 1
            offset++
        }
        return -1
    }

    private fun scanMarkupName(text: String, start: Int): Int {
        var offset = start
        while (offset < text.length && (text[offset].isLetterOrDigit() || text[offset] in "_:-.")) offset++
        return offset
    }

    private data class MarkupTagResult(val endOffset: Int, val endState: MarkupLexicalState)
}

private fun token(start: Int, end: Int, category: EditorTokenCategory): EditorToken {
    return EditorToken(start, end.coerceAtLeast(start), category)
}
