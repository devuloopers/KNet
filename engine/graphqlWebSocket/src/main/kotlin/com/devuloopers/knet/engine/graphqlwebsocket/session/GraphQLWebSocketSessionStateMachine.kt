package com.devuloopers.knet.engine.graphqlwebsocket.session

import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelope
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketMessageType
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLOperationType
import com.devuloopers.knet.traffic.model.TrafficDirection

/** Compact operation identity correlated across messages on one GraphQL WebSocket connection. */
data class GraphQLWebSocketOperationIdentity(
    /** Multiplexed operation ID carried by protocol envelopes. */
    val id: String,
    /** Explicit or document-derived GraphQL operation name. */
    val name: String?,
    /** Query, mutation, or subscription category parsed from the authored document. */
    val type: GraphQLOperationType,
)

/** Semantic result after a message advances one connection-confined state machine. */
data class GraphQLWebSocketMessageSemantics(
    /** Strictly parsed envelope. */
    val envelope: GraphQLWebSocketEnvelope,
    /** Correlated operation identity, when this message belongs to an active operation. */
    val operation: GraphQLWebSocketOperationIdentity?,
)

/** Protocol lifecycle violation that must fail an authored session closed. */
class GraphQLWebSocketProtocolException(message: String) : IllegalStateException(message)

/**
 * Connection-confined modern GraphQL WebSocket lifecycle and operation correlator.
 *
 * The caller must serialize invocations for one socket. No transport bytes or JSON payloads are retained.
 */
class GraphQLWebSocketSessionStateMachine(
    private val parser: GraphQLWebSocketEnvelopeParser,
    private val maximumActiveOperations: Int = DEFAULT_MAXIMUM_ACTIVE_OPERATIONS,
) {
    private var initialized = false
    private var acknowledged = false
    private var terminal = false
    private val operations = linkedMapOf<String, GraphQLWebSocketOperationIdentity>()

    init {
        require(maximumActiveOperations in 1..10_000) { "Active GraphQL operation limit is invalid." }
    }

    /** Whether the server acknowledgement required before subscription execution has arrived. */
    val isAcknowledged: Boolean
        get() = acknowledged

    /** Current bounded active operation identities in insertion order. */
    val activeOperations: List<GraphQLWebSocketOperationIdentity>
        get() = operations.values.toList()

    /** Validates and applies one directional envelope, returning its correlated semantic identity. */
    fun accept(
        direction: TrafficDirection,
        envelope: GraphQLWebSocketEnvelope,
    ): GraphQLWebSocketMessageSemantics {
        checkProtocol(!terminal) { "The GraphQL WebSocket session is already terminal." }
        checkDirection(direction, envelope.type)
        val operation = when (envelope.type) {
            GraphQLWebSocketMessageType.CONNECTION_INIT -> {
                checkProtocol(!initialized) { "Only one connection_init message is allowed." }
                initialized = true
                null
            }
            GraphQLWebSocketMessageType.CONNECTION_ACK -> {
                checkProtocol(initialized && !acknowledged) {
                    "connection_ack requires one pending connection_init."
                }
                acknowledged = true
                null
            }
            GraphQLWebSocketMessageType.SUBSCRIBE -> {
                checkProtocol(acknowledged) { "subscribe requires connection acknowledgement." }
                checkProtocol(operations.size < maximumActiveOperations) { "Too many active GraphQL operations." }
                val operationId = checkNotNull(envelope.operationId)
                checkProtocol(operationId !in operations) { "GraphQL operation ID is already active." }
                val parsed = parser.operation(envelope)
                    ?: throw GraphQLWebSocketProtocolException("subscribe requires one valid GraphQL operation.")
                GraphQLWebSocketOperationIdentity(operationId, parsed.name, parsed.type).also { identity ->
                    operations[operationId] = identity
                }
            }
            GraphQLWebSocketMessageType.NEXT -> active(envelope)
            GraphQLWebSocketMessageType.ERROR -> active(envelope).also { operations.remove(it.id) }
            GraphQLWebSocketMessageType.COMPLETE -> active(envelope).also { operations.remove(it.id) }
            GraphQLWebSocketMessageType.PING,
            GraphQLWebSocketMessageType.PONG -> null
        }
        return GraphQLWebSocketMessageSemantics(envelope, operation)
    }

    /** Terminalizes the connection and releases all operation correlation state. */
    fun close() {
        terminal = true
        operations.clear()
    }

    private fun active(envelope: GraphQLWebSocketEnvelope): GraphQLWebSocketOperationIdentity {
        val operationId = checkNotNull(envelope.operationId)
        return operations[operationId]
            ?: throw GraphQLWebSocketProtocolException("GraphQL operation ID is not active.")
    }

    private fun checkDirection(direction: TrafficDirection, type: GraphQLWebSocketMessageType) {
        val valid = when (direction) {
            TrafficDirection.CLIENT_TO_SERVER -> type in CLIENT_TYPES
            TrafficDirection.SERVER_TO_CLIENT -> type in SERVER_TYPES
        }
        checkProtocol(valid) { "GraphQL WebSocket message type is invalid for this direction." }
    }

    private inline fun checkProtocol(value: Boolean, lazyMessage: () -> String) {
        if (!value) throw GraphQLWebSocketProtocolException(lazyMessage())
    }

    companion object {
        /** Default active-operation bound for one multiplexed connection. */
        const val DEFAULT_MAXIMUM_ACTIVE_OPERATIONS: Int = 1_024

        private val CLIENT_TYPES = setOf(
            GraphQLWebSocketMessageType.CONNECTION_INIT,
            GraphQLWebSocketMessageType.SUBSCRIBE,
            GraphQLWebSocketMessageType.COMPLETE,
            GraphQLWebSocketMessageType.PING,
            GraphQLWebSocketMessageType.PONG,
        )
        private val SERVER_TYPES = setOf(
            GraphQLWebSocketMessageType.CONNECTION_ACK,
            GraphQLWebSocketMessageType.NEXT,
            GraphQLWebSocketMessageType.ERROR,
            GraphQLWebSocketMessageType.COMPLETE,
            GraphQLWebSocketMessageType.PING,
            GraphQLWebSocketMessageType.PONG,
        )
    }
}
