package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.application.port.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.port.inspection.SemanticInspector
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream

/** Asynchronous bounded GraphQL semantic inspector for captured HTTP exchanges. */
class GraphQLSemanticInspector(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : SemanticInspector {
    override val id: InspectorId = InspectorId("graphql")
    override val schemaVersion: Long = 1L
    override val priority: Int = 100
    override val bodyBudgetBytes: Int = 1_048_576

    override fun supports(exchange: HttpExchangeSnapshot): Boolean {
        val request = exchange.request.head
        val target = when (val value = request.target) {
            is RequestTarget.Absolute -> value.pathAndQuery
            is RequestTarget.Origin -> value.pathAndQuery
            is RequestTarget.Custom -> value.value
            else -> ""
        }
        val contentType = request.headers.firstOrNull {
            it.name.value.equals("Content-Type", ignoreCase = true)
        }?.value.orEmpty()
        return request.method.token.equals("POST", ignoreCase = true) ||
            target.contains("graphql", ignoreCase = true) ||
            contentType.contains("graphql", ignoreCase = true)
    }

    override suspend fun inspect(input: SemanticInspectionInput): InspectionDocument? {
        val target = input.exchange.request.head.target.displayValue()
        val contentType = input.exchange.request.head.headers.firstOrNull {
            it.name.value.equals("Content-Type", ignoreCase = true)
        }?.value.orEmpty()
        val bodyBytes = input.requestBody?.chunks.orEmpty().let { chunks ->
            ByteArrayOutputStream(chunks.sumOf { it.size }).use { output ->
                chunks.forEach { output.write(it.copyBytes()) }
                output.toByteArray()
            }
        }
        if (bodyBytes.isEmpty()) {
            return if (target.contains("graphql", ignoreCase = true) ||
                contentType.contains("graphql", ignoreCase = true)
            ) {
                InspectionDocument(kind = "graphql", title = "GraphQL request")
            } else {
                null
            }
        }
        val bodyText = runCatching { bodyBytes.decodeToString().trim() }.getOrNull() ?: return null
        if (!bodyText.startsWith('{') || !bodyText.endsWith('}')) return null
        val root = runCatching { objectMapper.readTree(bodyText) }.getOrNull() ?: return null
        val queryNode = root.get("query")?.takeIf { it.isTextual } ?: return null
        val rawQuery = queryNode.asText().trim().takeIf(String::isNotBlank) ?: return null
        val operationName = root.get("operationName")
            ?.takeIf { it.isTextual }
            ?.asText()
            ?.takeIf(String::isNotBlank)
            ?: extractOperationName(rawQuery)
        val operationType = when {
            rawQuery.startsWith("mutation", ignoreCase = true) -> "Mutation"
            rawQuery.startsWith("subscription", ignoreCase = true) -> "Subscription"
            else -> "Query"
        }
        val summary = rawQuery.replace("\n", " ").replace("\\s+".toRegex(), " ").take(256)
        return InspectionDocument(
            kind = "graphql",
            title = operationName?.let { "GraphQL $operationType: $it" } ?: "GraphQL $operationType",
            summary = summary,
            fields = listOfNotNull(
                InspectionField("Operation type", operationType),
                operationName?.let { InspectionField("Operation name", it) },
                InspectionField("Request target", target),
                input.requestBody?.truncated?.takeIf { it }?.let { InspectionField("Body", "Preview truncated") },
            ),
        )
    }

    private fun extractOperationName(query: String): String? {
        val trimmed = query.trimStart()
        for (keyword in listOf("query", "mutation", "subscription")) {
            if (trimmed.startsWith(keyword, ignoreCase = true)) {
                val remainder = trimmed.substring(keyword.length).trimStart()
                val end = remainder.indexOfAny(charArrayOf('(', '{', ' ', '\n'))
                return remainder.substring(0, if (end > 0) end else remainder.length).trim().takeIf(String::isNotBlank)
            }
        }
        return null
    }
}

private fun RequestTarget.displayValue(): String = when (this) {
    is RequestTarget.Absolute -> pathAndQuery
    is RequestTarget.Origin -> pathAndQuery
    is RequestTarget.AuthorityForm -> authority.host
    RequestTarget.Asterisk -> "*"
    is RequestTarget.Custom -> value
}
