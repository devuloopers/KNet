package com.devuloopers.knet.editor.highlighter

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Multiline tokenization state indices matching RSyntaxTextArea AbstractJFlexCTokenMaker.
 */
enum class TokenState {
    NULL,
    IN_MULTILINE_COMMENT,
    IN_DOUBLE_QUOTE,
    IN_SINGLE_QUOTE,
    IN_TEMPLATE_STRING
}

/**
 * Result of tokenizing a single line with state propagation.
 *
 * @property annotatedString Styled line string with syntax token colors.
 * @property endState The line-ending token state passed to the next line.
 */
data class TokenLineResult(
    val annotatedString: AnnotatedString,
    val endState: TokenState
)

/**
 * Character-by-Character Finite State Machine (FSM) Tokenizer matching RSyntaxTextArea TokenMaker.
 *
 * Processes lines sequentially and passes token state indices across line boundaries.
 */
object TokenMaker {

    private val KEYWORDS = setOf(
        "function", "return", "var", "let", "const", "if", "else", "for", "while",
        "async", "await", "try", "catch", "new", "test", "assert", "val", "fun",
        "import", "class", "typeof", "instanceof", "in", "of", "switch", "case", "default"
    )

    private val BOOLEANS = setOf("true", "false", "null", "undefined")

    /**
     * Checks if characters between [start] (inclusive) and [end] (exclusive) are exclusively whitespace.
     * Zero-allocation replacement for string substring + trim.
     */
    fun isOnlyWhitespaceBetween(line: String, start: Int, end: Int): Boolean {
        if (start < 0 || end > line.length || start >= end) return true
        for (idx in start until end) {
            if (!line[idx].isWhitespace()) return false
        }
        return true
    }

    /**
     * Tokenizes full document text line-by-line with state propagation across line breaks.
     */
    fun tokenizeDocument(text: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        val lines = text.split("\n")
        val builder = AnnotatedString.Builder()
        var currentState = TokenState.NULL

        for (i in lines.indices) {
            val line = lines[i]
            val result = tokenizeLine(line, currentState)
            builder.append(result.annotatedString)
            currentState = result.endState

            if (i < lines.size - 1) {
                builder.append("\n")
            }
        }

        return builder.toAnnotatedString()
    }

    /**
     * Tokenizes a single line of text starting from [startState].
     */
    fun tokenizeLine(line: String, startState: TokenState): TokenLineResult {
        if (line.isEmpty()) return TokenLineResult(AnnotatedString(""), startState)

        val builder = AnnotatedString.Builder(line)
        var state = startState
        var i = 0
        val n = line.length

        while (i < n) {
            when (state) {
                TokenState.IN_MULTILINE_COMMENT -> {
                    val commentStart = i
                    var commentEnd = n
                    while (i < n - 1) {
                        if (line[i] == '*' && line[i + 1] == '/') {
                            i += 2
                            commentEnd = i
                            state = TokenState.NULL
                            break
                        }
                        i++
                    }
                    if (state == TokenState.IN_MULTILINE_COMMENT) {
                        i = n
                    }
                    builder.addStyle(SpanStyle(color = CodeSyntaxColors.Comment), commentStart, commentEnd)
                }

                TokenState.IN_DOUBLE_QUOTE -> {
                    val strStart = i
                    i++ // Advance past opening quote
                    var strEnd = n
                    while (i < n) {
                        if (line[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (line[i] == '"') {
                            i++
                            strEnd = i
                            state = TokenState.NULL
                            break
                        }
                        i++
                    }
                    builder.addStyle(SpanStyle(color = CodeSyntaxColors.String), strStart, strEnd)
                }

                TokenState.IN_SINGLE_QUOTE -> {
                    val strStart = i
                    i++ // Advance past opening quote
                    var strEnd = n
                    while (i < n) {
                        if (line[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (line[i] == '\'') {
                            i++
                            strEnd = i
                            state = TokenState.NULL
                            break
                        }
                        i++
                    }
                    builder.addStyle(SpanStyle(color = CodeSyntaxColors.String), strStart, strEnd)
                }

                TokenState.IN_TEMPLATE_STRING -> {
                    val strStart = i
                    i++ // Advance past opening quote
                    var strEnd = n
                    while (i < n) {
                        if (line[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (line[i] == '`') {
                            i++
                            strEnd = i
                            state = TokenState.NULL
                            break
                        }
                        i++
                    }
                    builder.addStyle(SpanStyle(color = CodeSyntaxColors.String), strStart, strEnd)
                }

                TokenState.NULL -> {
                    val c = line[i]

                    // Check Single-Line Comment //
                    if (c == '/' && i < n - 1 && line[i + 1] == '/') {
                        builder.addStyle(SpanStyle(color = CodeSyntaxColors.Comment), i, n)
                        i = n
                        break
                    }

                    // Check Multiline Comment Start /*
                    if (c == '/' && i < n - 1 && line[i + 1] == '*') {
                        state = TokenState.IN_MULTILINE_COMMENT
                        continue
                    }

                    // Check String Starters
                    if (c == '"') {
                        // Check if JSON Key ("key":) with zero allocation whitespace check
                        val colonIndex = line.indexOf(':', i + 1)
                        val nextQuote = line.indexOf('"', i + 1)
                        if (nextQuote != -1 && colonIndex != -1 && colonIndex > nextQuote && isOnlyWhitespaceBetween(line, nextQuote + 1, colonIndex)) {
                            val keyEnd = colonIndex + 1
                            builder.addStyle(SpanStyle(color = CodeSyntaxColors.Key, fontWeight = FontWeight.Bold), i, nextQuote + 1)
                            builder.addStyle(SpanStyle(color = CodeSyntaxColors.Separator), colonIndex, colonIndex + 1)
                            i = keyEnd
                            continue
                        }
                        state = TokenState.IN_DOUBLE_QUOTE
                        continue
                    }

                    if (c == '\'') {
                        state = TokenState.IN_SINGLE_QUOTE
                        continue
                    }

                    if (c == '`') {
                        state = TokenState.IN_TEMPLATE_STRING
                        continue
                    }

                    // Identifiers, Keywords, Numbers, Booleans
                    if (c.isLetter() || c == '_' || c == '$') {
                        val wordStart = i
                        while (i < n && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$')) {
                            i++
                        }
                        val word = line.substring(wordStart, i)
                        when {
                            KEYWORDS.contains(word) -> builder.addStyle(SpanStyle(color = CodeSyntaxColors.Keyword, fontWeight = FontWeight.Bold), wordStart, i)
                            BOOLEANS.contains(word) -> builder.addStyle(SpanStyle(color = CodeSyntaxColors.Boolean, fontWeight = FontWeight.Bold), wordStart, i)
                            else -> builder.addStyle(SpanStyle(color = CodeSyntaxColors.Identifier), wordStart, i)
                        }
                        continue
                    }

                    if (c.isDigit()) {
                        val numStart = i
                        while (i < n && (line[i].isDigit() || line[i] == '.')) {
                            i++
                        }
                        builder.addStyle(SpanStyle(color = CodeSyntaxColors.Number), numStart, i)
                        continue
                    }

                    if ("{}[]():,".contains(c)) {
                        builder.addStyle(SpanStyle(color = CodeSyntaxColors.Separator), i, i + 1)
                    }

                    i++
                }
            }
        }

        return TokenLineResult(builder.toAnnotatedString(), state)
    }
}

/**
 * High-Performance Visual Transformation with LRU caching for 0ms scroll latency.
 *
 * Caches tokenized [TransformedText] results keyed by text content hash. Reuses cached
 * instances during scrolling and cursor movements without re-tokenizing.
 */
class FsmTokenMakerVisualTransformation(
    private val maxCacheEntries: Int = 32
) : VisualTransformation {

    private val cache = object : LinkedHashMap<Int, TransformedText>(maxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TransformedText>?): Boolean {
            return size > maxCacheEntries
        }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val key = text.text.hashCode()
        synchronized(cache) {
            val cached = cache[key]
            if (cached != null) {
                return cached
            }
        }

        val highlighted = TokenMaker.tokenizeDocument(text.text)
        val transformed = TransformedText(
            text = highlighted,
            offsetMapping = OffsetMapping.Identity
        )

        synchronized(cache) {
            cache[key] = transformed
        }

        return transformed
    }
}
