package com.devuloopers.knet.domain.payload

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType

/**
 * Domain strategy contract for bidirectional payload transformation between
 * raw transport payload strings and strongly-typed structured models [S].
 *
 * @param S Strongly-typed state representation (e.g. GraphQlState, ProtobufState).
 */
public interface PayloadMapper<S> {

    /**
     * Target [RequestBodyType] handled by this mapper strategy.
     */
    public val bodyType: RequestBodyType

    /**
     * Parses raw transport payload text into a structured state model [S].
     *
     * @param payloadText Raw transport body payload string.
     * @return Formatted state model [S].
     */
    public fun parsePayload(payloadText: String): S

    /**
     * Serializes a structured state model [S] back into raw transport payload text.
     *
     * @param state Target state model [S].
     * @return Serialized transport payload string.
     */
    public fun serializePayload(state: S): String
}
