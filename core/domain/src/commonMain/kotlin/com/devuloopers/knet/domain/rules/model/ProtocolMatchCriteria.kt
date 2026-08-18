package com.devuloopers.knet.domain.rules.model

/**
 * Extensible protocol-specific interception matching criteria strategy.
 */
sealed interface ProtocolMatchCriteria {

    /**
     * Standard HTTP/REST matching criteria (URL regex / HTTP method).
     */
    object HttpDefault : ProtocolMatchCriteria

    /**
     * GraphQL specific matching criteria.
     *
     * @property operationName Optional target GraphQL operationName filter (e.g. "GetUserProfile").
     */
    data class GraphQL(
        val operationName: String? = null
    ) : ProtocolMatchCriteria

    /**
     * gRPC specific matching criteria for future protocol extensions.
     *
     * @property serviceName Target gRPC package/service name filter.
     * @property methodName Target gRPC RPC method name filter.
     */
    data class Grpc(
        val serviceName: String? = null,
        val methodName: String? = null
    ) : ProtocolMatchCriteria

    /**
     * WebSocket frame specific criteria for future protocol extensions.
     *
     * @property frameType Target frame type filter.
     */
    data class WebSocket(
        val frameType: String? = null
    ) : ProtocolMatchCriteria
}
