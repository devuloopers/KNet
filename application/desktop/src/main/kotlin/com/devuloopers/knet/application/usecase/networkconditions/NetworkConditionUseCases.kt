package com.devuloopers.knet.application.usecase.networkconditions

import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionBody
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsResult
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.TrafficBodyPreview
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import com.devuloopers.knet.domain.policy.PolicyWorkspace

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
        public val protocolCriteria: NetworkConditionProtocolCriteria,
        public val workspace: PolicyWorkspace = PolicyWorkspace.Other,
        public val workspaceName: String = "Other traffic",
        public val definition: com.devuloopers.knet.domain.policy.PolicyDefinition<NetworkConditionRule>? = null,
    ) : PrepareNetworkConditionRuleResult

    public data object MissingExchange : PrepareNetworkConditionRuleResult
    public data object DestinationUnavailable : PrepareNetworkConditionRuleResult
}

/** Resolves a trusted canonical destination for the Traffic one-click rule editor. */
public class PrepareNetworkConditionRuleUseCase(
    private val trafficQuery: TrafficQuery,
    private val repository: NetworkConditionsRepository,
    private val loadTrafficExchangeDetails: LoadTrafficExchangeDetailsUseCase =
        LoadTrafficExchangeDetailsUseCase(trafficQuery),
    private val protocolRegistry: NetworkConditionProtocolRegistry = NetworkConditionProtocolRegistry(),
    private val inspectionAnnotations: InspectionAnnotationStore? = null,
) {
    public suspend fun execute(exchangeId: ExchangeId): PrepareNetworkConditionRuleResult {
        val exchange = trafficQuery.getExchange(exchangeId)
        val connectionId = exchange?.connectionId ?: trafficQuery.getOpaqueFlow(OpaqueFlowId(exchangeId.value))?.connectionId
        val workspace = PolicyWorkspace.fromIngress(connectionId?.let { trafficQuery.getConnectionIngress(it)?.ingress })
        val scoped = repository.forWorkspace(workspace)
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
        val catalog = scoped.observeCatalog().first { it.ready }
        val configuration = catalog.configuration
        val protocolCriteria = exchange?.let { captured ->
            when (val loaded = loadTrafficExchangeDetails.execute(exchangeId)) {
                is LoadTrafficExchangeDetailsResult.Found -> protocolRegistry.suggestCriteria(
                    NetworkConditionHttpInspectionInput(
                        request = captured.request,
                        requestBody = loaded.details.requestBody.toNetworkConditionBody(),
                        requestBodyComplete = loaded.details.requestBody.isComplete(),
                        trafficAnnotations = loadAnnotations(exchangeId),
                    ),
                )
                LoadTrafficExchangeDetailsResult.Missing -> null
            }
        } ?: NetworkConditionProtocolCriteria.TransportDefault
        val matchingRules = configuration.rules.asSequence().filter { rule ->
            rule.target == conditionTarget && rule.protocolCriteria == protocolCriteria
        }
        // Replacement retains saved alternatives. Reopen the active choice for this device before
        // falling back to an inactive definition; merely opening an inactive rule never enables it.
        val existing = matchingRules.firstOrNull { it.enabled } ?: matchingRules.firstOrNull()
        val suggested = existing?.profileId
            ?: com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns.NO_THROTTLING.id
        return PrepareNetworkConditionRuleResult.Ready(
            target = conditionTarget,
            existingRuleId = existing?.id,
            suggestedProfileId = suggested,
            protocolCriteria = protocolCriteria, workspace = workspace, workspaceName = catalog.workspaceName,
            definition = existing?.let { rule -> catalog.definitions.firstOrNull { it.rule.id == rule.id } },
        )
    }

    private suspend fun loadAnnotations(exchangeId: ExchangeId) = try {
        inspectionAnnotations?.get(exchangeId).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun TrafficBodyPreview.toNetworkConditionBody(): NetworkConditionBody? = when (this) {
    TrafficBodyPreview.Empty -> NetworkConditionBody(ByteArray(0))
    is TrafficBodyPreview.Available -> NetworkConditionBody(chunk.copyBytes())
    is TrafficBodyPreview.Unavailable,
    TrafficBodyPreview.ReadFailed,
    -> null
}

private fun TrafficBodyPreview.isComplete(): Boolean = when (this) {
    TrafficBodyPreview.Empty -> true
    is TrafficBodyPreview.Available -> chunk.endOfBody
    is TrafficBodyPreview.Unavailable,
    TrafficBodyPreview.ReadFailed,
    -> false
}
