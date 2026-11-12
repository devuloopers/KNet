package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.application.contract.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.contract.inspection.SemanticInspector
import com.devuloopers.knet.application.contract.inspection.GraphQLInspectionMetadata
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.http.RequestTarget

/** Stable generic annotation identities shared by GraphQL Traffic presentation and rule suggestions. */
internal object GraphQLInspectionSchema {
    val inspectorId: InspectorId = InspectorId("graphql")
    const val VERSION: Long = 2L
    const val DOCUMENT_KIND: String = GraphQLInspectionMetadata.DOCUMENT_KIND
    const val OPERATION_TYPE_FIELD: String = "Operation type"
    const val OPERATION_NAME_FIELD: String = "Operation name"
    const val BATCH_SIZE_FIELD: String = "Batch size"
    const val REQUEST_TARGET_FIELD: String = GraphQLInspectionMetadata.REQUEST_TARGET_FIELD
    const val BODY_FIELD: String = "Body"
    const val SOURCE_FIELD: String = "Source"
    const val DIAGNOSTIC_FIELD: String = "Inspection"
}

/** Asynchronous bounded GraphQL semantic inspector for captured HTTP exchanges. */
class GraphQLSemanticInspector(
    private val parser: GraphQLDocumentParser = GraphQLDocumentParser(),
) : SemanticInspector {
    override val id: InspectorId = GraphQLInspectionSchema.inspectorId
    override val schemaVersion: Long = GraphQLInspectionSchema.VERSION
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
            request.method.token.equals("GET", ignoreCase = true) && GraphQLHttpDecoder.hasGraphQLParameters(target) ||
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
        val document = parser.decodeHttp(input.exchange.request.head.method.token, target, contentType, bodyBytes,
            bodyComplete = input.requestBody?.truncated != true) ?: return null
        val primary = document.selectedOperation ?: document.matchableOperations.firstOrNull()
        val operationType = primary?.type?.label
        val operationName = primary?.name
        val title = if (document.matchableOperations.size > 1) {
            "GraphQL batch: ${document.matchableOperations.size} operations"
        } else if (operationType != null) {
            operationName?.let { "GraphQL $operationType: $it" } ?: "GraphQL $operationType"
        } else "GraphQL request"
        return InspectionDocument(
            kind = GraphQLInspectionSchema.DOCUMENT_KIND,
            title = title,
            summary = document.queryText.take(2_048).replace(Regex("\\s+"), " ").take(256).takeIf(String::isNotBlank),
            fields = listOfNotNull(
                operationType?.let { InspectionField(GraphQLInspectionSchema.OPERATION_TYPE_FIELD, it) },
                operationName?.let { InspectionField(GraphQLInspectionSchema.OPERATION_NAME_FIELD, it) },
                document.matchableOperations.size.takeIf { it > 1 }?.let {
                    InspectionField(GraphQLInspectionSchema.BATCH_SIZE_FIELD, it.toString())
                },
                InspectionField(GraphQLInspectionSchema.SOURCE_FIELD, document.source.label),
                document.diagnostic?.let { InspectionField(GraphQLInspectionSchema.DIAGNOSTIC_FIELD, it.description) },
                InspectionField(GraphQLInspectionSchema.REQUEST_TARGET_FIELD, target),
            ),
        )
    }
}

private fun RequestTarget.displayValue(): String = when (this) {
    is RequestTarget.Absolute -> pathAndQuery
    is RequestTarget.Origin -> pathAndQuery
    is RequestTarget.AuthorityForm -> authority.host
    RequestTarget.Asterisk -> "*"
    is RequestTarget.Custom -> value
}
