package com.devuloopers.knet.engine.graphqlwebsocket.descriptor

import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import java.net.URI

/** Adds the `GQL WS` identity when a handshake requests the modern GraphQL WebSocket protocol. */
class GraphQLWebSocketRequestDescriptorStrategy : RequestDescriptorStrategy {
    override val priority: Int = 450

    override fun describe(request: RequestDescriptorInput): RequestDescriptorContribution? {
        val hinted = request.semanticKindHint == RequestKindId.GRAPHQL_WEBSOCKET
        val requested = request.headers.asSequence()
            .filter { header -> header.name.value.equals(SUBPROTOCOL_HEADER, ignoreCase = true) }
            .flatMap { header -> header.value.split(',').asSequence() }
            .map(String::trim)
            .any { token -> token == GRAPHQL_TRANSPORT_WS_SUBPROTOCOL }
        if (!hinted && !requested) return null
        val path = runCatching { URI.create(request.absoluteUrl).rawPath }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: "/graphql"
        return RequestDescriptorContribution(
            kind = RequestKindId.GRAPHQL_WEBSOCKET,
            badgeLabel = "GQL WS",
            suggestedName = path,
        )
    }

    private companion object {
        const val SUBPROTOCOL_HEADER: String = "sec-websocket-protocol"
    }
}
