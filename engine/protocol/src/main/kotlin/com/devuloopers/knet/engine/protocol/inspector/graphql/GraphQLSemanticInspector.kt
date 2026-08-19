package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.application.port.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.port.inspection.SemanticInspector
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.http.RequestTarget

/** Asynchronous bounded GraphQL semantic inspector for captured HTTP exchanges. */
class GraphQLSemanticInspector(
    private val parser: GraphQLDocumentParser = GraphQLDocumentParser(),
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
            val bytes = ByteArray(chunks.sumOf { it.size })
            var destinationOffset = 0
            chunks.forEach { chunk ->
                val source = chunk.copyBytes()
                source.copyInto(bytes, destinationOffset)
                destinationOffset += source.size
            }
            bytes
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
        val document = parser.parse(bodyBytes) ?: return null
        val primary = document.operations.first()
        val operationType = primary.type.displayName()
        val operationName = primary.name
        val title = if (document.operations.size > 1) {
            "GraphQL batch: ${document.operations.size} operations"
        } else {
            operationName?.let { "GraphQL $operationType: $it" } ?: "GraphQL $operationType"
        }
        return InspectionDocument(
            kind = "graphql",
            title = title,
            summary = primary.summary,
            fields = listOfNotNull(
                InspectionField("Operation type", operationType),
                operationName?.let { InspectionField("Operation name", it) },
                document.operations.size.takeIf { it > 1 }?.let {
                    InspectionField("Batch size", it.toString())
                },
                InspectionField("Request target", target),
                input.requestBody?.truncated?.takeIf { it }?.let { InspectionField("Body", "Preview truncated") },
            ),
        )
    }
}

private fun GraphQLOperationType.displayName(): String = when (this) {
    GraphQLOperationType.QUERY -> "Query"
    GraphQLOperationType.MUTATION -> "Mutation"
    GraphQLOperationType.SUBSCRIPTION -> "Subscription"
}

private fun RequestTarget.displayValue(): String = when (this) {
    is RequestTarget.Absolute -> pathAndQuery
    is RequestTarget.Origin -> pathAndQuery
    is RequestTarget.AuthorityForm -> authority.host
    RequestTarget.Asterisk -> "*"
    is RequestTarget.Custom -> value
}
