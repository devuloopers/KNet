package com.devuloopers.knet.domain.protocol.model

/**
 * Closed domain contract representing protocol metadata detected on intercepted network traffic.
 *
 * Used by proxy interceptors and traffic loggers to categorize specialized protocols
 * (such as GraphQL, gRPC, or Protobuf) and surface rich metadata in traffic views.
 */
public sealed interface InterceptionMetadata {

    /**
     * Standard HTTP/HTTPS network transaction without specialized protocol metadata.
     */
    public data object GenericHttp : InterceptionMetadata

    /**
     * Strongly-typed metadata representing an intercepted GraphQL operation.
     *
     * @property operationName Extracted GraphQL operation name (e.g. "GetUserProfile"), or null if anonymous.
     * @property operationType Operation classification ("Query", "Mutation", or "Subscription").
     * @property querySummary Truncated summary of the GraphQL query string for log displays.
     */
    public data class GraphQL(
        val operationName: String?,
        val operationType: String = "Query",
        val querySummary: String = ""
    ) : InterceptionMetadata

    /**
     * Strongly-typed metadata representing an intercepted gRPC call.
     *
     * @property serviceName Fully-qualified gRPC service name.
     * @property methodName Target gRPC method name.
     */
    public data class Grpc(
        val serviceName: String,
        val methodName: String
    ) : InterceptionMetadata

    /**
     * Strongly-typed metadata representing an intercepted binary Protobuf payload.
     *
     * @property messageType Optional decoded message type descriptor name.
     */
    public data class Protobuf(
        val messageType: String? = null
    ) : InterceptionMetadata
}
