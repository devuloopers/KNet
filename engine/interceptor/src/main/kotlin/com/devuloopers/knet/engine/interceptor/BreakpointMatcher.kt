package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.matchesTransaction

/**
 * Pure rule evaluation component finding active matching rules for Netty proxy engine.
 * Delegates strictly to domain [RuleModel.matchesTransaction] Single Source of Truth.
 */
object BreakpointMatcher {

    /**
     * Finds the first active matching request breakpoint rule.
     */
    fun findMatchingRequestRule(url: String, method: String, requestBodyText: String? = null): RuleModel? {
        if (!BreakpointRuleRegistry.isGlobalInterceptionEnabled.value) return null
        return BreakpointRuleRegistry.getRules().firstOrNull { rule ->
            rule.matchesTransaction(
                url = url,
                method = method,
                currentPhase = BreakpointPhase.REQUEST,
                requestBodyText = requestBodyText
            )
        }
    }

    /**
     * Finds the first active matching response breakpoint rule.
     */
    fun findMatchingResponseRule(url: String, method: String, requestBodyText: String? = null): RuleModel? {
        if (!BreakpointRuleRegistry.isGlobalInterceptionEnabled.value) return null
        return BreakpointRuleRegistry.getRules().firstOrNull { rule ->
            rule.matchesTransaction(
                url = url,
                method = method,
                currentPhase = BreakpointPhase.RESPONSE,
                requestBodyText = requestBodyText
            )
        }
    }
}
