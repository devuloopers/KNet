package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.domain.protocol.inspector.ProtocolInspector
import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Protocol inspector strategy detecting intercepted GraphQL query, mutation, and subscription requests.
 *
 * Evaluates HTTP POST payloads containing JSON root objects with a textual "query" field.
 */
class GraphQLProtocolInspector(
    private val objectMapper: ObjectMapper = ObjectMapper()
) : ProtocolInspector {

    override val priority: Int = 100

    override fun inspect(
        method: String,
        url: String,
        headers: Map<String, String>,
        bodyBytes: ByteArray
    ): InterceptionMetadata? {
        val hasUrlMatch = url.contains("graphql", ignoreCase = true)
        val hasHeaderMatch = headers.entries.any {
            it.key.equals("content-type", ignoreCase = true) && it.value.contains("graphql", ignoreCase = true)
        }

        if (!method.equals("POST", ignoreCase = true) && !hasUrlMatch && !hasHeaderMatch) {
            return null
        }

        if (bodyBytes.isEmpty()) {
            return if (hasUrlMatch || hasHeaderMatch) {
                InterceptionMetadata.GraphQL(
                    operationName = null,
                    operationType = "Query",
                    querySummary = ""
                )
            } else {
                null
            }
        }

        val bodyText = try {
            bodyBytes.decodeToString().trim()
        } catch (_: Exception) {
            return null
        }

        if (!bodyText.startsWith("{") || !bodyText.endsWith("}")) return null

        return try {
            val root = objectMapper.readTree(bodyText) ?: return null
            val hasQueryField = root.has("query") && root.get("query").isTextual
            if (!hasQueryField) return null

            val rawQuery = root.get("query").asText().trim()
            if (rawQuery.isBlank()) return null

            val operationName = if (root.has("operationName") && root.get("operationName").isTextual) {
                root.get("operationName").asText().ifBlank { null }
            } else {
                extractOperationNameFromQuery(rawQuery)
            }

            val operationType = when {
                rawQuery.startsWith("mutation", ignoreCase = true) -> "Mutation"
                rawQuery.startsWith("subscription", ignoreCase = true) -> "Subscription"
                else -> "Query"
            }

            val summary = rawQuery.replace("\n", " ").replace("\\s+".toRegex(), " ").take(80)

            InterceptionMetadata.GraphQL(
                operationName = operationName,
                operationType = operationType,
                querySummary = summary
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractOperationNameFromQuery(query: String): String? {
        val trimmed = query.trimStart()
        val keywords = listOf("query", "mutation", "subscription")
        for (kw in keywords) {
            if (trimmed.startsWith(kw, ignoreCase = true)) {
                val afterKeyword = trimmed.substring(kw.length).trimStart()
                val nameEnd = afterKeyword.indexOfAny(charArrayOf('(', '{', ' ', '\n'))
                if (nameEnd > 0) {
                    val extracted = afterKeyword.substring(0, nameEnd).trim()
                    if (extracted.isNotBlank()) return extracted
                }
            }
        }
        return null
    }
}
