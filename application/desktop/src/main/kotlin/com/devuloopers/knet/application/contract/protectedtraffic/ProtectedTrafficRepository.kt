package com.devuloopers.knet.application.contract.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.ProtectedServiceGroupId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import kotlinx.coroutines.flow.StateFlow

/** Durable source of the atomic protected-traffic policy snapshot consumed by runtime adapters. */
public interface ProtectedTrafficRepository {
    /** Current validated configuration, including immutable built-in compatibility rules. */
    public val configuration: StateFlow<ProtectedTrafficConfiguration>

    /** Persists the global fallback [action]. */
    public suspend fun setDefaultAction(action: ProtectedTrafficAction)

    /** Creates or replaces one user-authored [rule] after duplicate validation. */
    public suspend fun upsertRule(rule: ProtectedTrafficRule)

    /** Removes the user rule identified by [ruleId]. */
    public suspend fun deleteRule(ruleId: ProtectedTrafficRuleId)

    /** Enables or disables one known built-in compatibility [groupId]. */
    public suspend fun setBuiltInGroupEnabled(groupId: ProtectedServiceGroupId, enabled: Boolean)
}
