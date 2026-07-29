package com.devuloopers.knet.editor.highlighter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Syntax color palette matching RSyntaxTextArea dark theme.
 */
object CodeSyntaxColors {
    val Key = Color(0xFF79C0FF)       // Bright Cyan-Blue for JSON/Map keys
    val String = Color(0xFFA5D6FF)    // Soft Pastel Blue for string literals
    val Number = Color(0xFFD2A8FF)    // Pastel Purple for numbers/integers
    val Boolean = Color(0xFFFFAB70)   // Coral-Orange for true/false/null
    val Keyword = Color(0xFFFF7B72)   // Soft Red for function/if/const/var/return
    val Comment = Color(0xFF8B949E)   // Slate Grey for comments
    val Identifier = Color(0xFFE6EDF3)// Clean White-Grey for variables/normal text
    val Separator = Color(0xFF7D8590) // Muted Silver for braces and colons
}

/**
 * High-performance regex tokenization engine for live editor syntax highlighting.
 */
object CodeSyntaxHighlighter {

    private val JSON_KEY_REGEX = Regex("\"[^\"]+\"\\s*:")
    private val STRING_REGEX = Regex("\"[^\"]*\"|'[^']*'")
    private val NUMBER_REGEX = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val BOOLEAN_REGEX = Regex("\\b(true|false|null|undefined)\\b")
    private val KEYWORD_REGEX = Regex("\\b(function|return|var|let|const|if|else|for|while|async|await|try|catch|new|test|assert|val|fun|import|class)\\b")
    private val COMMENT_REGEX = Regex("//.*|/\\*[\\s\\S]*?\\*/")

    /**
     * Highlights [text] string according to RSyntaxTextArea token colors.
     */
    fun highlight(text: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            append(text)

            // Default style for normal text
            addStyle(SpanStyle(color = CodeSyntaxColors.Identifier), 0, text.length)

            // 1. Comments (highest priority)
            COMMENT_REGEX.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = CodeSyntaxColors.Comment), match.range.first, match.range.last + 1)
            }

            // 2. Keywords
            KEYWORD_REGEX.findAll(text).forEach { match ->
                if (!isInsideComment(match.range.first, text)) {
                    addStyle(
                        SpanStyle(color = CodeSyntaxColors.Keyword, fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // 3. Strings
            STRING_REGEX.findAll(text).forEach { match ->
                if (!isInsideComment(match.range.first, text)) {
                    addStyle(SpanStyle(color = CodeSyntaxColors.String), match.range.first, match.range.last + 1)
                }
            }

            // 4. JSON Keys (override string style for keys before ':')
            JSON_KEY_REGEX.findAll(text).forEach { match ->
                if (!isInsideComment(match.range.first, text)) {
                    val keyEnd = match.value.lastIndexOf('"')
                    if (keyEnd != -1) {
                        addStyle(
                            SpanStyle(color = CodeSyntaxColors.Key, fontWeight = FontWeight.Bold),
                            match.range.first,
                            match.range.first + keyEnd + 1
                        )
                    }
                }
            }

            // 5. Numbers
            NUMBER_REGEX.findAll(text).forEach { match ->
                if (!isInsideComment(match.range.first, text) && !isInsideString(match.range.first, text)) {
                    addStyle(SpanStyle(color = CodeSyntaxColors.Number), match.range.first, match.range.last + 1)
                }
            }

            // 6. Booleans & Null
            BOOLEAN_REGEX.findAll(text).forEach { match ->
                if (!isInsideComment(match.range.first, text) && !isInsideString(match.range.first, text)) {
                    addStyle(
                        SpanStyle(color = CodeSyntaxColors.Boolean, fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }
        }
    }

    private fun isInsideComment(offset: Int, text: String): Boolean {
        val lineStart = text.lastIndexOf('\n', offset).let { if (it == -1) 0 else it + 1 }
        val lineToOffset = text.substring(lineStart, offset)
        return lineToOffset.contains("//")
    }

    private fun isInsideString(offset: Int, text: String): Boolean {
        val lineStart = text.lastIndexOf('\n', offset).let { if (it == -1) 0 else it + 1 }
        val lineToOffset = text.substring(lineStart, offset)
        val quoteCount = lineToOffset.count { it == '"' || it == '\'' }
        return quoteCount % 2 != 0
    }
}

/**
 * Compose [VisualTransformation] that applies RSyntaxTextArea syntax token colors during live typing.
 */
class CodeSyntaxVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = CodeSyntaxHighlighter.highlight(text.text)
        return TransformedText(
            text = highlighted,
            offsetMapping = OffsetMapping.Identity
        )
    }
}
