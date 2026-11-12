package com.devuloopers.knet.engine.formatter.descriptor

import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter

/**
 * Describes GraphQL requests using the same envelope and AST parser as body formatting.
 *
 * Anonymous operations retain `GQL` identity while allowing the HTTP contribution to provide a path-based name.
 */
class GraphQlRequestDescriptorStrategy : RequestDescriptorStrategy {
    override val priority: Int = 100

    override fun describe(request: RequestDescriptorInput): RequestDescriptorContribution? {
        val contentType = request.headers.firstOrNull {
            it.name.value.equals(CONTENT_TYPE_HEADER, ignoreCase = true)
        }?.value.orEmpty()
        val document = GraphQLHttpRequestFormatter.inspect(request.transportMethod.token, request.absoluteUrl,
            contentType, request.body?.copyBytes(), request.bodyComplete)
        if (document == null && request.semanticKindHint != RequestKindId.GRAPHQL) return null
        return RequestDescriptorContribution(
            kind = RequestKindId.GRAPHQL,
            badgeLabel = "GQL",
            suggestedName = document?.selectedOperation?.name?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private companion object {
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val GRAPHQL_TARGET_HINT = "graphql"
        const val GRAPHQL_MEDIA_TYPE_HINT = "graphql"
    }
}
