package com.devuloopers.knet.domain.payload

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType

/**
 * Domain strategy contract for bidirectional transformation between raw transport payload
 * strings and strongly-typed [StructuredPayloadState] models.
 *
 * Implemented by protocol-specific handlers (e.g. GraphQL, Protobuf, Avro) to parse wire formats
 * and serialize structured editor states without generic type erasure or runtime casting.
 */
interface PayloadStrategy {

    /**
     * Target [RequestBodyType] handled by this payload strategy.
     */
    val bodyType: RequestBodyType

    /**
     * Parses raw transport payload text into a strongly-typed [StructuredPayloadState].
     *
     * @param rawText Raw transport body payload string.
     * @return Strongly-typed [StructuredPayloadState] instance.
     */
    fun parse(rawText: String): StructuredPayloadState

    /**
     * Serializes a strongly-typed [StructuredPayloadState] back into a valid transport payload string.
     *
     * @param state Target [StructuredPayloadState] instance.
     * @return Serialized transport payload string.
     */
    fun serialize(state: StructuredPayloadState): String
}
