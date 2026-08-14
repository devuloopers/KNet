package com.devuloopers.knet.ui.desktop.breakpointmanager.mapper

import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointRuleUiModel

/**
 * Maps domain [RuleModel] to presentation [BreakpointRuleUiModel].
 */
fun RuleModel.toUiModel(): BreakpointRuleUiModel {
    val rawMethod = action
    val methodEnum = if (rawMethod.isBlank() || rawMethod.equals("ALL", ignoreCase = true)) {
        null
    } else {
        try {
            HttpMethod.valueOf(rawMethod.uppercase())
        } catch (_: Exception) {
            HttpMethod.CUSTOM
        }
    }

    return BreakpointRuleUiModel(
        id = id,
        urlPattern = condition.ifBlank { ".*" },
        method = methodEnum,
        phase = type,
        enabled = enabled,
        protocolCriteria = protocolCriteria
    )
}

/**
 * Maps presentation [BreakpointRuleUiModel] to domain [RuleModel].
 */
fun BreakpointRuleUiModel.toDomainRule(): RuleModel {
    return RuleModel(
        id = id,
        name = urlPattern,
        type = phase,
        condition = urlPattern,
        action = method?.name ?: "ALL",
        enabled = enabled,
        protocolCriteria = protocolCriteria
    )
}
