package com.devuloopers.knet.engine.formatter.descriptor

import com.devuloopers.knet.domain.apistudio.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.apistudio.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.apistudio.descriptor.RequestKindId
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
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

    override fun describe(request: SavedApiRequest): RequestDescriptorContribution? {
        if (request.body.type != RequestBodyType.GRAPHQL) return null
        val formatted = formatter.format(
            headers = request.headers.filter { it.isEnabled }.associate { it.key to it.value },
            bodyText = request.body.content
        ) as? BodyFormat.GraphQL
        return RequestDescriptorContribution(
            kind = RequestKindId.GRAPHQL,
            badgeLabel = "GQL",
            suggestedName = formatted?.operationName?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}
