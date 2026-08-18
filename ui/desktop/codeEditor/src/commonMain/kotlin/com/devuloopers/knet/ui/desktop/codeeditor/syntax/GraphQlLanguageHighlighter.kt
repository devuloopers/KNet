package com.devuloopers.knet.ui.desktop.codeeditor.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Syntax highlighter strategy for GraphQL documents (Query, Mutation, Subscription, Schema).
 *
 * Tokenizes keywords, variables ($var), directives (@dir), scalar types (ID, String),
 * strings, numbers, booleans, and comments into styled AnnotatedString tokens.
 */
class GraphQlLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "graphql"

    private val GRAPHQL_KEYWORD_REGEX = Regex("\\b(query|mutation|subscription|fragment|on|type|schema|extend|input|enum|interface|union|scalar|implements|directive)\\b")
    private val VARIABLE_REGEX = Regex("\\$[a-zA-Z0-9_]+")
    private val DIRECTIVE_REGEX = Regex("@[a-zA-Z0-9_]+")
    private val TYPE_REGEX = Regex("\\b(ID|String|Int|Boolean|Float)\\b")
    private val COMMENT_REGEX = Regex("#.*")
    private val STRING_REGEX = Regex("\"[^\"]*\"|'[^']*'")
    private val NUMBER_REGEX = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val BOOLEAN_REGEX = Regex("\\b(true|false|null)\\b")

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val foldRanges = mutableMapOf<Int, Int>()
        val stack = ArrayDeque<Int>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.endsWith("{") || trimmed.endsWith("(")) {
                stack.addLast(index)
            } else if (trimmed.startsWith("}") || trimmed.startsWith(")")) {
                if (stack.isNotEmpty()) {
                    val start = stack.removeLast()
                    foldRanges[start] = index
                }
            }
        }
        return foldRanges
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex < 0 || endLineIndex >= lines.size) return ""
        val endLineTrimmed = lines[endLineIndex].trim()
        return when {
            endLineTrimmed.startsWith("}") -> "} "
            endLineTrimmed.startsWith(")") -> ") "
            else -> ""
        }
    }

    override fun highlightLine(lineText: String): AnnotatedString {
        if (lineText.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            append(lineText)
            addStyle(SpanStyle(color = CodeSyntaxColors.Identifier), 0, lineText.length)

            // 1. Comments
            COMMENT_REGEX.findAll(lineText).forEach { match ->
                addStyle(SpanStyle(color = CodeSyntaxColors.Comment), match.range.first, match.range.last + 1)
            }

            // 2. Keywords
            GRAPHQL_KEYWORD_REGEX.findAll(lineText).forEach { match ->
                if (!isInsideComment(match.range.first, lineText)) {
                    addStyle(
                        SpanStyle(color = CodeSyntaxColors.Keyword, fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // 3. Variables ($ids, $partner)
            VARIABLE_REGEX.findAll(lineText).forEach { match ->
                if (!isInsideComment(match.range.first, lineText)) {
                    addStyle(
                        SpanStyle(color = CodeSyntaxColors.Boolean, fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // 4. Directives (@include, @skip)
            DIRECTIVE_REGEX.findAll(lineText).forEach { match ->
                if (!isInsideComment(match.range.first, lineText)) {
                    addStyle(
                        SpanStyle(color = CodeSyntaxColors.Number, fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // 5. Types (ID, String, Int, Boolean, Float)
            TYPE_REGEX.findAll(lineText).forEach { match ->
                if (!isInsideComment(match.range.first, lineText)) {
                    addStyle(
                        SpanStyle(color = CodeSyntaxColors.Key, fontWeight = FontWeight.SemiBold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // 6. Strings
            STRING_REGEX.findAll(lineText).forEach { match ->
                if (!isInsideComment(match.range.first, lineText)) {
                    addStyle(SpanStyle(color = CodeSyntaxColors.String), match.range.first, match.range.last + 1)
                }
            }

            // 7. Numbers
            NUMBER_REGEX.findAll(lineText).forEach { match ->
                if (!isInsideComment(match.range.first, lineText) && !isInsideString(match.range.first, lineText)) {
                    addStyle(SpanStyle(color = CodeSyntaxColors.Number), match.range.first, match.range.last + 1)
                }
            }

            // 8. Booleans & Null
            BOOLEAN_REGEX.findAll(lineText).forEach { match ->
                if (!isInsideComment(match.range.first, lineText) && !isInsideString(match.range.first, lineText)) {
                    addStyle(SpanStyle(color = CodeSyntaxColors.Boolean, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
            }
        }
    }

    private fun isInsideComment(index: Int, text: String): Boolean {
        val commentIndex = text.indexOf('#')
        return commentIndex != -1 && index > commentIndex
    }

    private fun isInsideString(index: Int, text: String): Boolean {
        val stringMatches = STRING_REGEX.findAll(text)
        return stringMatches.any { index >= it.range.first && index <= it.range.last }
    }
}
