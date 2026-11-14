package com.devuloopers.knet.application.contract.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.devuloopers.knet.domain.policy.*

/** Durable source of the atomic protected-traffic policy snapshot consumed by runtime adapters. */
public interface ProtectedTrafficRepository {
    /** Resets a workspace override; shared definitions remain available for every other device. */
    public suspend fun resetRule(id: ProtectedTrafficRuleId): Unit = deleteRule(id)

    /** Deletes the definition with explicit shared-usage acknowledgement, rather than toggling a device. */
    public suspend fun deleteDefinition(id: ProtectedTrafficRuleId, context: RuleEditContext, acknowledgeSharedUsage: Boolean = false) {
        forWorkspace(context.workspace).deleteRule(id)
    }

    /** Atomic manager projection with availability, revisions and per-workspace activation. */
    public fun observeCatalog(): Flow<ProtectionPolicyCatalog> = configuration.map { config ->
        ProtectionPolicyCatalog(PolicyWorkspace.Other, "Other traffic", config, config.rules.map { rule ->
            PolicyDefinition(rule, RuleAvailability.Private(PolicyWorkspace.Other), 1L,
                if (rule.enabled) setOf(PolicyWorkspace.Other) else emptySet())
        })
    }

    /** A workspace-bound adapter whose mutations cannot be retargeted by tab selection. */
    public fun forWorkspace(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace): ProtectedTrafficRepository {
        require(workspace == com.devuloopers.knet.domain.policy.PolicyWorkspace.Other)
        return this
    }
    /** Persists definition ownership and this workspace's activation atomically. */
    public suspend fun saveDefinition(rule: ProtectedTrafficRule, context: com.devuloopers.knet.domain.policy.RuleEditContext): RuleSaveResult {
        require(context.workspace == com.devuloopers.knet.domain.policy.PolicyWorkspace.Other)
        upsertRule(rule)
        return RuleSaveResult()
    }
    /** Current validated configuration containing user rules and any imported legacy exceptions. */
    public val configuration: StateFlow<ProtectedTrafficConfiguration>

    /** Atomically persists [action] and whether the global Inspect default permits temporary TLS fallback. */
    public suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean = false)

    /** Creates or replaces one user-authored [rule] after duplicate validation. */
    public suspend fun upsertRule(rule: ProtectedTrafficRule)

    /** Removes the user rule identified by [ruleId]. */
    public suspend fun deleteRule(ruleId: ProtectedTrafficRuleId)

}

/** Consistent feature configuration and definition metadata for one editing workspace. */
public data class ProtectionPolicyCatalog(
    public val workspace: PolicyWorkspace,
    public val workspaceName: String,
    public val configuration: ProtectedTrafficConfiguration,
    public val definitions: List<PolicyDefinition<ProtectedTrafficRule>>,
    public val ready: Boolean = true,
    /** Display-only workspace names for impact diagnostics. */
    public val workspaceNames: Map<PolicyWorkspace, String> = emptyMap(),
)
