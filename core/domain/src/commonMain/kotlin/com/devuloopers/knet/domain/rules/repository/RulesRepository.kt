package com.devuloopers.knet.domain.rules.repository

import com.devuloopers.knet.domain.rules.model.BreakpointRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.devuloopers.knet.domain.policy.*

/**
 * Feature repository contract for the Rules engine.
 */
interface RulesRepository {
    /** Deletes the definition with explicit shared-usage acknowledgement, rather than toggling a device. */
    suspend fun deleteDefinition(id: String, context: RuleEditContext, acknowledgeSharedUsage: Boolean = false) {
        forWorkspace(context.workspace).deleteRule(id)
    }

    /** A committed library view; activation flags belong to this workspace. */
    fun observeCatalog(): Flow<PolicyRuleCatalog<BreakpointRule>> = rulesFlow.map { rules ->
        PolicyRuleCatalog(PolicyWorkspace.Other, "Other traffic", rules.map { rule ->
            PolicyDefinition(rule, RuleAvailability.Private(PolicyWorkspace.Other), 1L,
                if (rule.enabled) setOf(PolicyWorkspace.Other) else emptySet())
        })
    }

    /** Returns an immutable workspace-bound adapter; it never reads the current UI selection at write time. */
    fun forWorkspace(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace): RulesRepository {
        require(workspace == com.devuloopers.knet.domain.policy.PolicyWorkspace.Other)
        return this
    }

    /** Saves availability and selected-device activation together. */
    suspend fun saveDefinition(rule: BreakpointRule, context: com.devuloopers.knet.domain.policy.RuleEditContext): RuleSaveResult {
        require(context.workspace == com.devuloopers.knet.domain.policy.PolicyWorkspace.Other)
        saveRule(rule)
        return RuleSaveResult()
    }

    /**
     * Reactive stream emitting persistent active interceptor rules.
     */
    val rulesFlow: Flow<List<BreakpointRule>>

    /**
     * Reactive stream emitting global proxy interception engine status.
     */
    val isGlobalInterceptionEnabled: Flow<Boolean>

    /**
     * Toggles global proxy interception engine state.
     */
    suspend fun toggleGlobalInterception(enabled: Boolean)

    /**
     * Toggles rule enabled/disabled state by ID.
     */
    suspend fun toggleRule(ruleId: String, enabled: Boolean)

    /**
     * Saves or updates a rule entity.
     */
    suspend fun saveRule(rule: BreakpointRule)

    /**
     * Deletes a rule entity by ID.
     */
    suspend fun deleteRule(ruleId: String)
}
