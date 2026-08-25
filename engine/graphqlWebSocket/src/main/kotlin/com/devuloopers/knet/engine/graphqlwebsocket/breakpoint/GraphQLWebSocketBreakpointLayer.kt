package com.devuloopers.knet.engine.graphqlwebsocket.breakpoint

import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.websocket.WebSocketProtocol
import com.devuloopers.knet.engine.websocket.WebSocketSemanticBreakpointLayer
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

/** Classifies valid modern GraphQL envelopes before raw WebSocket breakpoint matching. */
class GraphQLWebSocketBreakpointLayer(
    private val parser: GraphQLWebSocketEnvelopeParser,
) : WebSocketSemanticBreakpointLayer {
    override val protocolId = GraphQLWebSocketBreakpointProtocol.id
    override val priority: Int = 200

    override fun mayApply(request: HttpRequestSnapshot): Boolean =
        GRAPHQL_TRANSPORT_WS_SUBPROTOCOL in WebSocketProtocol.requestedSubprotocols(request.head)

    override fun applies(
        request: HttpRequestSnapshot,
        negotiatedSubprotocol: String?,
        kind: ProtocolMessageKind,
        direction: TrafficDirection,
        payload: ByteArray,
    ): Boolean = negotiatedSubprotocol == GRAPHQL_TRANSPORT_WS_SUBPROTOCOL &&
        kind == ProtocolMessageKind.TEXT &&
        parser.parse(payload) != null
}
