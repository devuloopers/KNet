package com.devuloopers.knet.engine.formatter.descriptor

import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat

/**
 * Describes GraphQL requests using the same envelope and AST parser as body formatting.
 *
 * Anonymous operations retain `GQL` identity while allowing the HTTP contribution to provide a path-based name.
 */
class GraphQlRequestDescriptorStrategy(
    private val formatter: GraphQLBodyFormatter = GraphQLBodyFormatter()
) : RequestDescriptorStrategy {
    override val priority: Int = 100

    override fun describe(request: RequestDescriptorInput): RequestDescriptorContribution? {
        val headers = request.headers.associate { it.name.value to it.value }
        val bodyText = request.body?.decodeToString().orEmpty()
        val contentType = request.headers.firstOrNull {
            it.name.value.equals(CONTENT_TYPE_HEADER, ignoreCase = true)
        }?.value.orEmpty()
        val isRecognized = request.semanticKindHint == RequestKindId.GRAPHQL ||
            request.absoluteUrl.contains(GRAPHQL_TARGET_HINT, ignoreCase = true) ||
            contentType.contains(GRAPHQL_MEDIA_TYPE_HINT, ignoreCase = true) ||
            bodyText.isNotBlank() && formatter.matches(headers, bodyText)
        if (!isRecognized) return null

        val formatted = bodyText.takeIf(String::isNotBlank)?.let {
            formatter.format(headers = headers, bodyText = it) as? BodyFormat.GraphQL
        }
        return RequestDescriptorContribution(
            kind = RequestKindId.GRAPHQL,
            badgeLabel = "GQL",
            suggestedName = formatted?.operationName?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private companion object {
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val GRAPHQL_TARGET_HINT = "graphql"
        const val GRAPHQL_MEDIA_TYPE_HINT = "graphql"
    }
}
