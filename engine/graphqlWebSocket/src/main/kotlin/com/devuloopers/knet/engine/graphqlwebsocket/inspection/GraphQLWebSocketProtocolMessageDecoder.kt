package com.devuloopers.knet.engine.graphqlwebsocket.inspection

import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadDecoder
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadDecoderId
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.application.contract.traffic.ProtocolMessageContextQuery
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLOperation
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelope
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
        val envelope = envelope(input) ?: return null
        val operation = parser.operation(envelope) ?: correlatedOperation(input, envelope)
        return ProtocolMessagePresentation(
            title = buildString {
                append(envelope.type.displayName())
                envelope.operationId?.let { id -> append(" ").append(id) }
                operation?.name?.let { name -> append(" - ").append(name) }
            },
            contentType = "application/json",
            text = parser.formatted(envelope),
            schemaName = operation?.type?.name,
            fields = buildList {
                add(InspectionField("Message", envelope.type.wireName))
                envelope.operationId?.let { add(InspectionField("Operation ID", it)) }
                operation?.name?.let { add(InspectionField("Operation", it)) }
                operation?.type?.let { add(InspectionField("Type", it.name.lowercase())) }
            },
        )
    }

    override fun contextQuery(input: ProtocolMessagePayloadInput): ProtocolMessageContextQuery? {
        val envelope = envelope(input) ?: return null
        if (envelope.operationId == null || envelope.type == GraphQLWebSocketMessageType.SUBSCRIBE) return null
        return ProtocolMessageContextQuery(input.message.exchangeId, input.message.id,
            protocolId, direction = null, kind = ProtocolMessageKind.TEXT)
    }

    private fun envelope(input: ProtocolMessagePayloadInput): GraphQLWebSocketEnvelope? {
        if (input.truncated || input.message.kind != ProtocolMessageKind.TEXT || input.parentExchange.id != input.message.exchangeId) return null
        val headers = input.parentExchange.response?.head?.headers.orEmpty().filter {
            it.name.value.equals(SUBPROTOCOL_HEADER, ignoreCase = true)
        }
        if (headers.singleOrNull()?.value?.trim() != GRAPHQL_TRANSPORT_WS_SUBPROTOCOL) return null
        return parser.parse(input.payload)
    }

    private fun correlatedOperation(input: ProtocolMessagePayloadInput, selected: GraphQLWebSocketEnvelope): GraphQLOperation? {
        val operationId = selected.operationId ?: return null
        // History is newest first in durable capture order. Stop on any evidence gap or an ended
        // operation, and use only a preceding client subscription for this exact multiplexed ID.
        for (entry in input.context) {
            val message = entry.message
            if (entry.truncated || message.exchangeId != input.message.exchangeId ||
                message.connectionId != input.message.connectionId || message.streamId != input.message.streamId ||
                message.protocol != protocolId || message.kind != ProtocolMessageKind.TEXT || message.id == input.message.id) return null
            val prior = entry.payload()?.let(parser::parse) ?: return null
            if (prior.operationId != operationId) continue
            if (prior.type == GraphQLWebSocketMessageType.COMPLETE || prior.type == GraphQLWebSocketMessageType.ERROR) return null
            if (prior.type == GraphQLWebSocketMessageType.SUBSCRIBE) {
                return if (message.direction == TrafficDirection.CLIENT_TO_SERVER) parser.operation(prior) else null
            }
        }
        return null
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
