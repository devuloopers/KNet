package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId

data class NetworkConditionRuleDraft(
    val hostPattern: String,
    val port: String,
    val existingRuleId: NetworkConditionRuleId?,
    val profileId: NetworkConditionProfileId,
    val enabled: Boolean = true,
)

data class NetworkConditionsState(
    val configuration: NetworkConditionConfiguration = NetworkConditionConfiguration(),
    val runtime: NetworkConditionRuntimeSnapshot = NetworkConditionRuntimeSnapshot(),
    val ruleDraft: NetworkConditionRuleDraft? = null,
    val errorMessage: String? = null,
)
