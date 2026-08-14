package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.language.AstPrinter
import graphql.parser.Parser

/**
 * Formatter for GraphQL requests.
 * Detects GraphQL query POST payloads or application/graphql headers,
 * extracts operationType, operationName, query string, and variables JSON.
 *
 * Also provides AST document syntax pretty-printing via `graphql-java`.
 */
class GraphQLBodyFormatter : BodyFormatter {

    override val priority: Int = 85

    private val objectMapper = ObjectMapper()

    /**
     * Formats a raw GraphQL query, mutation, or subscription document string into
     * spec-compliant multi-line indented GraphQL AST syntax via `graphql-java`.
     *
     * @param queryText Raw GraphQL query document text.
     * @return Pretty-printed GraphQL document syntax, or the trimmed raw string if syntax is malformed.
     */
    fun formatQuery(queryText: String): String {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) return ""

        return try {
            val document = Parser().parseDocument(trimmed)
            AstPrinter.printAst(document)
        } catch (_: Exception) {
            trimmed
        }
    }

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        if (contentType.contains("application/graphql", ignoreCase = true)) return true

        val trimmed = bodyText.trim()
        if (trimmed.isEmpty()) return false

        // Check if raw GraphQL document string or pre-formatted block
        if (trimmed.startsWith("query") ||
            trimmed.startsWith("mutation") ||
            trimmed.startsWith("subscription") ||
            trimmed.startsWith("fragment") ||
            trimmed.startsWith("# GraphQL")
        ) {
            return true
        }

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false

        return try {
            val root = objectMapper.readTree(trimmed)
            val hasQueryField = root.has("query") && root.get("query").isTextual
            if (!hasQueryField) return false

            val queryStr = root.get("query").asText().trim()
            queryStr.startsWith("query") ||
                queryStr.startsWith("mutation") ||
                queryStr.startsWith("subscription") ||
                queryStr.startsWith("fragment") ||
                queryStr.startsWith("{")
        } catch (_: Exception) {
            false
        }
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val trimmed = bodyText.trim()
        return try {
            val root = objectMapper.readTree(trimmed)
            val rawQuery = if (root.has("query") && root.get("query").isTextual) root.get("query").asText() else trimmed
            val operationName = if (root.has("operationName") && root.get("operationName").isTextual) root.get("operationName").asText() else null

            val variablesJson = if (root.has("variables") && root.get("variables").isObject) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root.get("variables"))
            } else ""

            val extensionsJson = if (root.has("extensions") && root.get("extensions").isObject) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root.get("extensions"))
            } else ""

            val (astOpType, astOpName) = parseAstDetails(rawQuery)
            val resolvedOperationName = operationName ?: astOpName

            val queryTrimmed = rawQuery.trim()
            val resolvedOperationType = when {
                queryTrimmed.startsWith("mutation") -> "Mutation"
                queryTrimmed.startsWith("subscription") -> "Subscription"
                else -> astOpType
            }

            val formattedQuery = formatQuery(rawQuery)

            BodyFormat.GraphQL(
                operationType = resolvedOperationType,
                operationName = resolvedOperationName,
                queryText = formattedQuery,
                variablesJson = variablesJson,
                extensionsJson = extensionsJson
            )
        } catch (_: Exception) {
            val (astOpType, astOpName) = parseAstDetails(trimmed)
            BodyFormat.GraphQL(
                operationType = astOpType,
                operationName = astOpName,
                queryText = formatQuery(trimmed),
                variablesJson = "",
                extensionsJson = ""
            )
        }
    }

    private fun parseAstDetails(queryText: String): Pair<String, String?> {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) return "Query" to null
        return try {
            val document = Parser().parseDocument(trimmed)
            val opDef = document.definitions.filterIsInstance<graphql.language.OperationDefinition>().firstOrNull()
            val opType = opDef?.operation?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Query"
            val opName = opDef?.name
            opType to opName
        } catch (_: Exception) {
            "Query" to null
        }
    }
}
