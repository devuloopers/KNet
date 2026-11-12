package com.devuloopers.knet.application.contract.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import kotlinx.coroutines.flow.StateFlow

/** Durable source of the atomic protected-traffic policy snapshot consumed by runtime adapters. */
public interface ProtectedTrafficRepository {
    /** Current validated configuration containing user rules and any imported legacy exceptions. */
    public val configuration: StateFlow<ProtectedTrafficConfiguration>

    /** Atomically persists [action] and whether the global Inspect default permits temporary TLS fallback. */
    public suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean = false)

    /** Creates or replaces one user-authored [rule] after duplicate validation. */
    public suspend fun upsertRule(rule: ProtectedTrafficRule)

    /** Removes the user rule identified by [ruleId]. */
    public suspend fun deleteRule(ruleId: ProtectedTrafficRuleId)

}
