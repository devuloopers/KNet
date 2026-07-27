package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Formatter for GraphQL requests.
 * Detects GraphQL query POST payloads or application/graphql headers,
 * extracts operationType, operationName, query string, and variables JSON.
 */
class GraphQLBodyFormatter : BodyFormatter {

    override val priority: Int = 85

    private val objectMapper = ObjectMapper()

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        if (contentType.contains("application/graphql", ignoreCase = true)) return true

        val trimmed = bodyText.trim()
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

            val queryTrimmed = rawQuery.trim()
            val operationType = when {
                queryTrimmed.startsWith("mutation") -> "Mutation"
                queryTrimmed.startsWith("subscription") -> "Subscription"
                else -> "Query"
            }

            BodyFormat.GraphQL(
                operationType = operationType,
                operationName = operationName,
                queryText = rawQuery,
                variablesJson = variablesJson
            )
        } catch (_: Exception) {
            BodyFormat.GraphQL(
                operationType = "Query",
                operationName = null,
                queryText = trimmed,
                variablesJson = ""
            )
        }
    }
}
