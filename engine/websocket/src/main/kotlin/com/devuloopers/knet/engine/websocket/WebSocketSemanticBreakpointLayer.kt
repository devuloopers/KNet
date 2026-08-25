package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

/**
 * Additive semantic breakpoint layer evaluated above one complete RFC 6455 message.
 *
 * Implementations classify only bounded complete messages. They do not own framing, persistence,
 * forwarding credit, or breakpoint decisions.
 */
interface WebSocketSemanticBreakpointLayer {
    /** Breakpoint extension identity inserted before the raw WebSocket transport identity. */
    val protocolId: BreakpointProtocolId

    /** Larger values are evaluated first when several semantic subprotocols recognize a message. */
    val priority: Int

    /** Cheap handshake-head hint used before the server selects a subprotocol. */
    fun mayApply(request: HttpRequestSnapshot): Boolean

    /** Returns whether this layer confidently owns the complete bounded [payload]. */
    fun applies(
        request: HttpRequestSnapshot,
        negotiatedSubprotocol: String?,
        kind: ProtocolMessageKind,
        direction: TrafficDirection,
        payload: ByteArray,
    ): Boolean
}
