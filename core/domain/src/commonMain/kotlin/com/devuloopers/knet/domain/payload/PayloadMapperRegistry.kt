package com.devuloopers.knet.domain.payload

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType

/**
 * Thread-safe domain registry evaluating registered [PayloadMapper] strategy implementations
 * for custom payload modes (GraphQL, Protobuf, Avro, etc.).
 *
 * @param mappers List of [PayloadMapper] strategy instances injected via Dependency Injection (Koin).
 */
public class PayloadMapperRegistry(
    mappers: List<PayloadMapper<*>> = emptyList()
) {
    private val registryMap: Map<RequestBodyType, PayloadMapper<*>> =
        mappers.associateBy { it.bodyType }

    /**
     * Resolves a registered [PayloadMapper] strategy for the given [RequestBodyType].
     *
     * @param bodyType Target [RequestBodyType].
     * @return Registered mapper strategy or null if no custom strategy is registered.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <S> getMapper(bodyType: RequestBodyType): PayloadMapper<S>? {
        return registryMap[bodyType] as? PayloadMapper<S>
    }
}
