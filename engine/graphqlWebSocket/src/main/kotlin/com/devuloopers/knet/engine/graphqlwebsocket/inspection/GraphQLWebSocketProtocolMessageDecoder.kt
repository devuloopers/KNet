package com.devuloopers.knet.engine.graphqlwebsocket.inspection

import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoder
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadDecoderId
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketMessageType
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

/** Semantic `graphql-transport-ws` presentation decoder that falls through to raw WebSocket. */
class GraphQLWebSocketProtocolMessageDecoder(
    private val parser: GraphQLWebSocketEnvelopeParser,
) : ProtocolMessagePayloadDecoder {
    override val decoderId: ProtocolMessagePayloadDecoderId = ProtocolMessagePayloadDecoderId("graphql-websocket")
    override val protocolId: MessageProtocolId = MessageProtocolId.WEBSOCKET
    override val priority: Int = 200

    override fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation? {
        if (input.message.kind != ProtocolMessageKind.TEXT) return null
        val selectedSubprotocol = input.parentExchange.response?.head?.headers?.firstOrNull { header ->
            header.name.value.equals(SUBPROTOCOL_HEADER, ignoreCase = true)
        }?.value?.trim()
        if (selectedSubprotocol != GRAPHQL_TRANSPORT_WS_SUBPROTOCOL) return null
        val envelope = parser.parse(input.payload) ?: return null
        val operation = parser.operation(envelope)
        return ProtocolMessagePresentation(
            title = buildString {
                append(envelope.type.displayName())
                envelope.operationId?.let { id -> append(" ").append(id) }
                operation?.name?.let { name -> append(" - ").append(name) }
            },
            contentType = "application/json",
            text = parser.formatted(envelope),
            schemaName = operation?.type?.name,
        )
    }

    private fun GraphQLWebSocketMessageType.displayName(): String = when (this) {
        GraphQLWebSocketMessageType.CONNECTION_INIT -> "GraphQL connection init"
        GraphQLWebSocketMessageType.CONNECTION_ACK -> "GraphQL connection acknowledged"
        GraphQLWebSocketMessageType.SUBSCRIBE -> "GraphQL subscribe"
        GraphQLWebSocketMessageType.NEXT -> "GraphQL next"
        GraphQLWebSocketMessageType.ERROR -> "GraphQL error"
        GraphQLWebSocketMessageType.COMPLETE -> "GraphQL complete"
        GraphQLWebSocketMessageType.PING -> "GraphQL ping"
        GraphQLWebSocketMessageType.PONG -> "GraphQL pong"
    }

    private companion object {
        const val SUBPROTOCOL_HEADER: String = "sec-websocket-protocol"
    }
}
