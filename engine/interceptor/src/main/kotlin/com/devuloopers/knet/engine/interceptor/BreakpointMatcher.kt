package com.devuloopers.knet.engine.interceptor

/**
 * Pure rule evaluation component finding active matching rules.
 */
object BreakpointMatcher {

    /**
     * Finds the first active matching request breakpoint rule.
     */
    fun findMatchingRequestRule(url: String, method: String): BreakpointRule? {
        if (!BreakpointRuleRegistry.isGlobalInterceptionEnabled.value) return null
        return BreakpointRuleRegistry.getRules().firstOrNull { rule ->
            rule.matches(url, method, BreakpointPhase.REQUEST)
        }
    }

    /**
     * Finds the first active matching response breakpoint rule.
     */
    fun findMatchingResponseRule(url: String, method: String): BreakpointRule? {
        if (!BreakpointRuleRegistry.isGlobalInterceptionEnabled.value) return null
        return BreakpointRuleRegistry.getRules().firstOrNull { rule ->
            rule.matches(url, method, BreakpointPhase.RESPONSE)
        }
    }
}
