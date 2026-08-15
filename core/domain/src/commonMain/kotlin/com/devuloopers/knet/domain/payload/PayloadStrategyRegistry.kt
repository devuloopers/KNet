package com.devuloopers.knet.domain.payload

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType

/**
 * Domain registry evaluating registered [PayloadStrategy] implementations for structured payload modes.
 *
 * Operates purely on [StructuredPayloadState] contracts with 100% compile-time type safety,
 * eliminating all unchecked casts and wildcard generics.
 *
 * @param strategies List of [PayloadStrategy] strategy instances provided by Dependency Injection.
 */
class PayloadStrategyRegistry(
    strategies: List<PayloadStrategy> = emptyList()
) {
    private val registryMap: Map<RequestBodyType, PayloadStrategy> =
        strategies.associateBy { it.bodyType }

    /**
     * Parses raw transport payload text into a strongly-typed [StructuredPayloadState] for the given [bodyType].
     *
     * @param bodyType Target [RequestBodyType].
     * @param rawText Raw transport payload string.
     * @return Structured payload state or [StructuredPayloadState.RawText] if no strategy is registered.
     */
    fun parse(bodyType: RequestBodyType, rawText: String): StructuredPayloadState {
        val strategy = registryMap[bodyType]
        return strategy?.parse(rawText) ?: StructuredPayloadState.RawText(rawText)
    }

    /**
     * Serializes a strongly-typed [StructuredPayloadState] into a transport payload string for the given [bodyType].
     *
     * @param bodyType Target [RequestBodyType].
     * @param state Target [StructuredPayloadState].
     * @return Serialized transport payload string.
     */
    fun serialize(bodyType: RequestBodyType, state: StructuredPayloadState): String {
        val strategy = registryMap[bodyType]
        return strategy?.serialize(state) ?: state.rawText
    }
}
