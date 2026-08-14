package com.devuloopers.knet.engine.formatter.graphql

import graphql.language.OperationDefinition
import graphql.parser.Parser

/**
 * High-performance domain utility for real-time extraction and bidirectional synchronization
 * of GraphQL operation names between user interface controls and query documents.
 *
 * Supports named and anonymous queries, mutations, subscriptions, variable definitions,
 * directives, and in-flight intermediate keystroke editing.
 */
object GraphQLQuerySynchronizer {

    private val NAMED_OP_REGEX = Regex(
        pattern = """(?m)^([ \t]*)(query|mutation|subscription)[ \t]+([A-Za-z0-9_]+)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val ANONYMOUS_KEYWORD_OP_REGEX = Regex(
        pattern = """(?m)^([ \t]*)(query|mutation|subscription)[ \t]*([\({])""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val BARE_SELECTION_SET_REGEX = Regex(
        pattern = """(?m)^([ \t]*)\{"""
    )

    /**
     * Extracts the primary operation name from a GraphQL query document.
     *
     * Leverages AST parsing with a resilient regex fallback to support partially typed queries during editing.
     *
     * @param queryText Raw GraphQL query string.
     * @return Extracted operation name (e.g. "GetUserProfile"), or null if the query is anonymous or empty.
     */
    fun extractOperationName(queryText: String): String? {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) return null

        // 1. Try resilient AST Parser first for complete documents
        try {
            val document = Parser().parseDocument(trimmed)
            val operationDef = document.definitions
                .filterIsInstance<OperationDefinition>()
                .firstOrNull()

            if (operationDef != null) {
                val name = operationDef.name
                if (!name.isNullOrBlank()) {
                    return name.trim()
                } else {
                    return null
                }
            }
        } catch (_: Exception) {
            // AST parsing might fail during live typing; gracefully fall back to regex
        }

        // 2. Regex fallback for in-flight / partially typed documents
        val lines = queryText.lines()
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("#") || trimmedLine.isEmpty()) continue

            val match = NAMED_OP_REGEX.find(trimmedLine)
            if (match != null) {
                val extracted = match.groupValues.getOrNull(3)?.trim()
                if (!extracted.isNullOrBlank()) {
                    return extracted
                }
            }
            // If the first non-comment non-empty line starts with { or anonymous query, it's anonymous
            break
        }

        return null
    }

    /**
     * Updates, adds, renames, or clears the operation name in a GraphQL query document
     * while preserving variable definitions, directives, indentation, and query body content.
     *
     * @param queryText Current GraphQL query text.
     * @param newOperationName New operation name to apply (or blank to clear).
     * @return Updated GraphQL query text.
     */
    fun updateOperationName(queryText: String, newOperationName: String): String {
        val sanitized = newOperationName.trim()
        val trimmedQuery = queryText.trim()

        // 1. Handle completely empty query text
        if (trimmedQuery.isEmpty()) {
            return if (sanitized.isNotEmpty()) {
                "query $sanitized {\n  \n}"
            } else {
                ""
            }
        }

        // 2. Case A: Query currently has a named operation (e.g. "query OldName(...) { ... }")
        if (NAMED_OP_REGEX.containsMatchIn(queryText)) {
            return if (sanitized.isNotEmpty()) {
                replaceFirstMatch(NAMED_OP_REGEX, queryText) { matchResult ->
                    val indent = matchResult.groupValues[1]
                    val opType = matchResult.groupValues[2]
                    "$indent$opType $sanitized"
                }
            } else {
                // Clear name: "query OldName(...) {" -> "query(...) {" or "query OldName {" -> "query {"
                replaceFirstMatch(NAMED_OP_REGEX, queryText) { matchResult ->
                    val indent = matchResult.groupValues[1]
                    val opType = matchResult.groupValues[2]
                    "$indent$opType"
                }
            }
        }

        // 3. Case B: Query has an anonymous operation keyword (e.g. "query(...) {" or "query {")
        if (ANONYMOUS_KEYWORD_OP_REGEX.containsMatchIn(queryText)) {
            if (sanitized.isNotEmpty()) {
                return replaceFirstMatch(ANONYMOUS_KEYWORD_OP_REGEX, queryText) { matchResult ->
                    val indent = matchResult.groupValues[1]
                    val opType = matchResult.groupValues[2]
                    val delimiter = matchResult.groupValues[3]
                    if (delimiter == "(") {
                        "$indent$opType $sanitized("
                    } else {
                        "$indent$opType $sanitized {"
                    }
                }
            } else {
                return queryText
            }
        }

        // 4. Case C: Query is a bare selection set starting with "{"
        if (BARE_SELECTION_SET_REGEX.containsMatchIn(queryText)) {
            if (sanitized.isNotEmpty()) {
                return replaceFirstMatch(BARE_SELECTION_SET_REGEX, queryText) { matchResult ->
                    val indent = matchResult.groupValues[1]
                    "${indent}query $sanitized {"
                }
            } else {
                return queryText
            }
        }

        // 5. Fallback: Prepend operation declaration
        return if (sanitized.isNotEmpty()) {
            "query $sanitized {\n$queryText\n}"
        } else {
            queryText
        }
    }

    private fun replaceFirstMatch(
        regex: Regex,
        input: String,
        transform: (MatchResult) -> String
    ): String {
        val match = regex.find(input) ?: return input
        val replacement = transform(match)
        return input.replaceRange(match.range, replacement)
    }
}
