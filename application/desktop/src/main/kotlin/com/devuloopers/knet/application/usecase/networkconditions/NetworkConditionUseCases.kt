package com.devuloopers.knet.application.usecase.networkconditions

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.flow.StateFlow

public class ObserveNetworkConditionsUseCase(private val repository: NetworkConditionsRepository) {
    public fun execute(): StateFlow<NetworkConditionConfiguration> = repository.configuration
}

public class SetNetworkConditionsEnabledUseCase(private val repository: NetworkConditionsRepository) {
    public suspend fun execute(enabled: Boolean): Unit = repository.setEnabled(enabled)
}

public class SetGlobalNetworkConditionUseCase(private val repository: NetworkConditionsRepository) {
    public suspend fun execute(profileId: NetworkConditionProfileId?): Unit = repository.setGlobalProfile(profileId)
}

public class SaveNetworkConditionRuleUseCase(private val repository: NetworkConditionsRepository) {
    public suspend fun execute(rule: NetworkConditionRule): Unit = repository.upsertRule(rule)
}

public class DeleteNetworkConditionRuleUseCase(private val repository: NetworkConditionsRepository) {
    public suspend fun execute(ruleId: NetworkConditionRuleId): Unit = repository.deleteRule(ruleId)
}

public sealed interface PrepareNetworkConditionRuleResult {
    public data class Ready(
        public val target: NetworkConditionTarget,
        public val existingRuleId: NetworkConditionRuleId?,
        public val suggestedProfileId: NetworkConditionProfileId,
    ) : PrepareNetworkConditionRuleResult

    public data object MissingExchange : PrepareNetworkConditionRuleResult
    public data object DestinationUnavailable : PrepareNetworkConditionRuleResult
}

/** Resolves a trusted canonical destination for the Traffic one-click rule editor. */
public class PrepareNetworkConditionRuleUseCase(
    private val trafficQuery: TrafficQuery,
    private val repository: NetworkConditionsRepository,
) {
    public suspend fun execute(exchangeId: ExchangeId): PrepareNetworkConditionRuleResult {
        val exchange = trafficQuery.getExchange(exchangeId)
        val destination = if (exchange != null) {
            when (val target = exchange.request.head.target) {
                is RequestTarget.Absolute -> {
                    val port = target.authority.port ?: when (target.scheme.token.lowercase()) {
                        "http" -> 80
                        "https" -> 443
                        else -> return PrepareNetworkConditionRuleResult.DestinationUnavailable
                    }
                    target.authority.host to port
                }
                is RequestTarget.AuthorityForm -> target.authority.port?.let { target.authority.host to it }
                    ?: return PrepareNetworkConditionRuleResult.DestinationUnavailable
                else -> return PrepareNetworkConditionRuleResult.DestinationUnavailable
            }
        } else {
            val flow = trafficQuery.getOpaqueFlow(OpaqueFlowId(exchangeId.value))
                ?: return PrepareNetworkConditionRuleResult.MissingExchange
            val port = flow.destination.port ?: return PrepareNetworkConditionRuleResult.DestinationUnavailable
            (flow.serverName ?: flow.destination.host) to port
        }
        val conditionTarget = runCatching {
            NetworkConditionTarget.parse(destination.first, destination.second)
        }.getOrElse { return PrepareNetworkConditionRuleResult.DestinationUnavailable }
        val configuration = repository.configuration.value
        val existing = configuration.rules.firstOrNull { it.target == conditionTarget }
        val suggested = existing?.profileId
            ?: configuration.lastQuickAddProfileId
            ?: com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns.STREAMING_100_KBPS.id
        return PrepareNetworkConditionRuleResult.Ready(conditionTarget, existing?.id, suggested)
    }
}
