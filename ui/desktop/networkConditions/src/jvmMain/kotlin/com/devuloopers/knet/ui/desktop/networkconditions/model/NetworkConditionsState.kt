package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId

data class NetworkConditionRuleDraft(
    val hostPattern: String,
    val port: String,
    val existingRuleId: NetworkConditionRuleId?,
    val profileId: NetworkConditionProfileId,
    val enabled: Boolean = true,
    val priority: String = "0",
    val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.TRANSPORT,
    val protocolValues: List<ProtocolCriteriaValue> = emptyList(),
)

data class NetworkConditionsState(
    val configuration: NetworkConditionConfiguration = NetworkConditionConfiguration(),
    val runtime: NetworkConditionRuntimeSnapshot = NetworkConditionRuntimeSnapshot(),
    val throughput: NetworkThroughputHistory = NetworkThroughputHistory(),
    val ruleDraft: NetworkConditionRuleDraft? = null,
    val protocolDefinitions: List<NetworkConditionProtocolDefinition> = emptyList(),
    val errorMessage: String? = null,
)
