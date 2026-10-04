package com.devuloopers.knet.application.usecase.networkconditions

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.CompiledNetworkConditionProtocolCriteria
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionMessageInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolObservation
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.domain.networkconditions.EffectiveNetworkCondition
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionMatcher
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule

/** UI-neutral protocol schema and criteria workflow for the Network Conditions rule editor. */
public class NetworkConditionProtocolRuleUseCase(
    private val registry: NetworkConditionProtocolRegistry,
) {
    /** Returns transport and installed semantic rule definitions. */
    public fun definitions(): List<NetworkConditionProtocolDefinition> = registry.definitions

    /** Decodes persisted criteria into extension-owned editor values. */
    public fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> =
        registry.editorValues(criteria)

    /** Validates editor values and creates persistable semantic criteria. */
    public fun createCriteria(
        protocolId: NetworkConditionProtocolId,
        values: List<ProtocolCriteriaValue>,
    ): NetworkConditionProtocolCriteria? = registry.createCriteria(protocolId, values)
}

/**
 * Resolves protocol-aware rules without giving extensions access to transport scheduling.
 *
 * The resolver inspects each eligible protocol once per HTTP request or framed message, then delegates final
 * priority and destination precedence to [NetworkConditionMatcher]. Unavailable or invalid semantic extensions
 * fail closed while ordinary destination and global fallback remain eligible.
 */
public class NetworkConditionSemanticResolver(
    private val registry: NetworkConditionProtocolRegistry,
) {
    /** Whether one enabled HTTP semantic rule can match [host] and [port]. */
    public fun requiresHttpInspection(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
    ): Boolean = semanticProtocolIds(
        configuration = configuration,
        host = host,
        port = port,
        unit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
    ).isNotEmpty()

    /** Whether one eligible HTTP semantic rule requires complete bounded request-body aggregation. */
    public fun requiresHttpBodyInspection(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
        input: NetworkConditionHttpInspectionInput? = null,
    ): Boolean = semanticProtocolIds(
        configuration = configuration,
        host = host,
        port = port,
        unit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
    ).any { protocolId ->
        if (input == null) {
            registry.requiresRequestBody(protocolId)
        } else {
            registry.shouldInspectHttpBody(protocolId, input)
        }
    }

    /** Whether one enabled framed-message rule can match [host] and [port]. */
    public fun requiresMessageInspection(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
    ): Boolean = semanticProtocolIds(
        configuration = configuration,
        host = host,
        port = port,
        unit = NetworkConditionInterceptionUnit.PROTOCOL_MESSAGE,
    ).isNotEmpty()

    /** Resolves a complete bounded HTTP request and its destination/global fallback. */
    public fun resolveHttp(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
        input: NetworkConditionHttpInspectionInput,
    ): EffectiveNetworkCondition? {
        val protocolIds = semanticProtocolIds(
            configuration = configuration,
            host = host,
            port = port,
            unit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
        )
        val observations = protocolIds.associateWith { protocolId -> registry.inspectHttp(protocolId, input) }
        return resolve(configuration, host, port, observations)
    }

    /**
     * Resolves one complete framed message and its destination/global fallback.
     *
     * [alwaysObserveProtocolId] lets a negotiated protocol retain bounded correlation while conditions are
     * bypassed, so a later live enable can affect an already subscribed operation without retaining payloads.
     */
    public fun resolveMessage(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
        input: NetworkConditionMessageInspectionInput,
        alwaysObserveProtocolId: NetworkConditionProtocolId? = null,
    ): EffectiveNetworkCondition? {
        val protocolIds = semanticProtocolIds(
            configuration = configuration,
            host = host,
            port = port,
            unit = NetworkConditionInterceptionUnit.PROTOCOL_MESSAGE,
        ) + listOfNotNull(alwaysObserveProtocolId)
        val observations = protocolIds.associateWith { protocolId -> registry.inspectMessage(protocolId, input) }
        return resolve(configuration, host, port, observations)
    }

    /** Releases bounded correlation state after one upgraded exchange terminates. */
    public fun releaseMessages(exchangeId: com.devuloopers.knet.traffic.id.ExchangeId) {
        registry.releaseMessages(exchangeId)
    }

    private fun resolve(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
        observations: Map<NetworkConditionProtocolId, NetworkConditionProtocolObservation?>,
    ): EffectiveNetworkCondition? {
        val compiled = mutableMapOf<NetworkConditionProtocolCriteria, CompiledNetworkConditionProtocolCriteria?>()
        return NetworkConditionMatcher.resolve(configuration, host, port) { criteria ->
            val matcher = compiled.getOrPut(criteria) { registry.compile(criteria) } ?: return@resolve false
            registry.matches(matcher, observations[criteria.protocolId])
        }
    }

    private fun semanticProtocolIds(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
        unit: NetworkConditionInterceptionUnit,
    ): Set<NetworkConditionProtocolId> {
        if (!configuration.enabled) return emptySet()
        return configuration.rules.asSequence()
            .filter(NetworkConditionRule::enabled)
            .filter { rule -> !rule.protocolCriteria.isTransportOnly && rule.target.matches(host, port) }
            .map { rule -> rule.protocolCriteria.protocolId }
            .filter { protocolId -> registry.interceptionUnit(protocolId) == unit }
            .toSet()
    }
}
