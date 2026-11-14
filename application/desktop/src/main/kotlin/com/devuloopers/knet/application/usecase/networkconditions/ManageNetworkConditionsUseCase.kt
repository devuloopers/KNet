package com.devuloopers.knet.application.usecase.networkconditions

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.policy.*

/**
 * Workspace-bound manager commands. ViewModels never select or persist policy through a repository.
 * Binding returns a lightweight command facade, freezing its identity before asynchronous work starts.
 */
public class ManageNetworkConditionsUseCase(private val repository: NetworkConditionsRepository) {
    /** Captures the exact device/Other traffic target. */
    public fun forWorkspace(workspace: PolicyWorkspace): ManageNetworkConditionsUseCase = ManageNetworkConditionsUseCase(repository.forWorkspace(workspace))
    /** Consistent library, activation, settings and usage observation. */
    public fun observeCatalog(): kotlinx.coroutines.flow.Flow<com.devuloopers.knet.application.contract.networkconditions.NetworkPolicyCatalog> = repository.observeCatalog()
    /** Enables shaping for this workspace. */
    public suspend fun setEnabled(enabled: Boolean): Unit = repository.setEnabled(enabled)
    /** Selects this workspace's baseline profile. */
    public suspend fun setGlobalProfile(id: NetworkConditionProfileId?): Unit = repository.setGlobalProfile(id)
    /** Traffic shortcut updates enablement and baseline atomically. */
    public suspend fun activateGlobalProfile(id: NetworkConditionProfileId?): Unit = repository.activateGlobalProfile(id)
    /** Adds or deliberately edits a reusable profile. */
    public suspend fun upsertProfile(profile: NetworkConditionProfile): Unit = repository.upsertProfile(profile)
    /** Refuses referenced profile deletion across all workspaces. */
    public suspend fun deleteProfile(id: NetworkConditionProfileId): Boolean = repository.deleteProfile(id)
    /** Updates activation, retaining the current definition. */
    public suspend fun upsertRule(rule: NetworkConditionRule): Unit = repository.upsertRule(rule)
    /** Deletes an unshared/unused definition using repository ownership checks. */
    public suspend fun deleteRule(id: NetworkConditionRuleId): Unit = repository.deleteRule(id)
    /** Saves the frozen definition revision and availability. */
    public suspend fun saveDefinition(rule: NetworkConditionRule, context: RuleEditContext): RuleSaveResult = repository.saveDefinition(rule, context)
    /** Deletes the acknowledged shared impact atomically. */
    public suspend fun deleteDefinition(id: NetworkConditionRuleId, context: RuleEditContext, acknowledgeSharedUsage: Boolean): Unit = repository.deleteDefinition(id, context, acknowledgeSharedUsage)
    /** Retains the editor's last profile choice only for this workspace. */
    public suspend fun setLastQuickAddProfile(id: NetworkConditionProfileId?): Unit = repository.setLastQuickAddProfile(id)
    /** Clears this workspace's shaping baseline while preserving authored libraries. */
    public suspend fun resetShaping(): Unit = repository.resetShaping()
}
