package com.devuloopers.knet.data.desktop.policy

import com.devuloopers.knet.application.contract.device.DevicePolicyLibrary
import com.devuloopers.knet.application.contract.device.ProjectedStateFlow
import com.devuloopers.knet.application.contract.device.profileUsage
import com.devuloopers.knet.application.contract.networkconditions.*
import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Lightweight immutable workspace adapters over one committed library. These are not separate engines.
 * Inactive presentation projections release replayed configuration; runtime consumes the library directly.
 */
class PolicyRepositoryViews(
    private val library: DevicePolicyLibrary,
    private val coordinator: BreakpointCoordinator,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val publisher = scope.launch {
        library.snapshot.collect { state ->
            coordinator.replaceWorkspaceRules(state.settings.keys.associateWith { workspace ->
                if (state.ready && state.settingsFor(workspace).interceptionEnabled)
                    state.breakpointRules(workspace).filter { it.enabled } else emptyList()
            })
        }
    }

    /** The returned adapter freezes the workspace in every mutation. */
    fun rules(workspace: PolicyWorkspace = PolicyWorkspace.Other): RulesRepository = RuleView(workspace)
    /** Profile definitions are shared; default selection and activation are scoped. */
    fun network(workspace: PolicyWorkspace = PolicyWorkspace.Other): NetworkConditionsRepository = NetworkView(workspace)
    /** Manual actions and default handling are scoped independently from definition visibility. */
    fun protection(workspace: PolicyWorkspace = PolicyWorkspace.Other): ProtectedTrafficRepository = ProtectionView(workspace)
    /** Stops only this publisher; product-owned scope and other components remain owned by product teardown. */
    override fun close() { publisher.cancel() }

    private inner class RuleView(private val workspace: PolicyWorkspace) : RulesRepository {
        override fun forWorkspace(workspace: PolicyWorkspace): RulesRepository = rules(workspace)
        override val rulesFlow: Flow<List<BreakpointRule>> = library.snapshot.map { it.breakpointRules(workspace) }.distinctUntilChanged()
        override val isGlobalInterceptionEnabled: Flow<Boolean> = library.snapshot.map { it.settingsFor(workspace).interceptionEnabled }.distinctUntilChanged()
        override fun observeCatalog(): Flow<PolicyRuleCatalog<BreakpointRule>> = library.snapshot.map { state ->
            PolicyRuleCatalog(workspace, state.workspaceNames[workspace] ?: "Saved device", state.breakpoints.filter { it.visibleTo(workspace) }, state.workspaceNames)
        }
        override suspend fun toggleGlobalInterception(enabled: Boolean) = library.updateSettings(workspace) { it.copy(interceptionEnabled = enabled) }
        override suspend fun toggleRule(ruleId: String, enabled: Boolean) = library.activate(workspace, PolicyRuleKind.BREAKPOINT, ruleId, enabled)
        override suspend fun saveRule(rule: BreakpointRule) {
            val existing = library.snapshot.first { it.ready }.breakpoints.firstOrNull { it.rule.id == rule.id }
            if (existing != null && existing.rule.copy(enabled = rule.enabled) == rule) toggleRule(rule.id, rule.enabled)
            else library.saveBreakpoint(workspace, rule, existing?.availability ?: RuleAvailability.Private(workspace), existing?.revision)
        }
        override suspend fun saveDefinition(rule: BreakpointRule, context: RuleEditContext) = library.saveBreakpoint(
            context.workspace, rule, context.availability, context.expectedRevision, true, context.activateAfterSave, context.expectedUsage)
        override suspend fun deleteDefinition(id: String, context: RuleEditContext, acknowledgeSharedUsage: Boolean) =
            library.delete(context.workspace, PolicyRuleKind.BREAKPOINT, id, checkNotNull(context.expectedRevision), acknowledgeSharedUsage, context.expectedUsage)

        override suspend fun deleteRule(ruleId: String) {
            val definition = library.snapshot.first { it.ready }.breakpoints.firstOrNull { it.rule.id == ruleId } ?: return
            library.delete(workspace, PolicyRuleKind.BREAKPOINT, ruleId, definition.revision)
        }
    }

    private inner class NetworkView(private val workspace: PolicyWorkspace) : NetworkConditionsRepository {
        override fun forWorkspace(workspace: PolicyWorkspace): NetworkConditionsRepository = network(workspace)
        override val configuration: StateFlow<NetworkConditionConfiguration> = ProjectedStateFlow(library.snapshot) { it.network(workspace) }
        override fun observeCatalog(): Flow<NetworkPolicyCatalog> = library.snapshot.map { state ->
            NetworkPolicyCatalog(workspace, state.workspaceNames[workspace] ?: "Saved device", state.network(workspace),
                state.conditions.filter { it.visibleTo(workspace) }, state.ready, state.workspaceNames, state.profiles.associate { it.id to state.profileUsage(it.id) })
        }
        override suspend fun setEnabled(enabled: Boolean) = library.updateSettings(workspace) { it.copy(networkEnabled = enabled) }
        override suspend fun setGlobalProfile(profileId: NetworkConditionProfileId?) = library.updateSettings(workspace) { it.copy(profileId = profileId) }
        override suspend fun activateGlobalProfile(profileId: NetworkConditionProfileId?) = library.updateSettings(workspace) { it.copy(networkEnabled = true, profileId = profileId) }
        override suspend fun upsertProfile(profile: NetworkConditionProfile) = library.saveProfile(profile)
        override suspend fun deleteProfile(profileId: NetworkConditionProfileId): Boolean = library.deleteProfile(profileId)
        override suspend fun upsertRule(rule: NetworkConditionRule) {
            val existing = library.snapshot.first { it.ready }.conditions.firstOrNull { it.rule.id == rule.id }
            if (existing != null && existing.rule.copy(enabled = rule.enabled) == rule)
                library.activate(workspace, PolicyRuleKind.NETWORK_CONDITION, rule.id.value, rule.enabled)
            else library.saveCondition(workspace, rule, existing?.availability ?: RuleAvailability.Private(workspace), existing?.revision)
        }
        override suspend fun saveDefinition(rule: NetworkConditionRule, context: RuleEditContext) = library.saveCondition(
            context.workspace, rule, context.availability, context.expectedRevision, true, context.activateAfterSave, context.expectedUsage)
        override suspend fun deleteDefinition(id: NetworkConditionRuleId, context: RuleEditContext, acknowledgeSharedUsage: Boolean) =
            library.delete(context.workspace, PolicyRuleKind.NETWORK_CONDITION, id.value, checkNotNull(context.expectedRevision), acknowledgeSharedUsage, context.expectedUsage)

        override suspend fun deleteRule(ruleId: NetworkConditionRuleId) {
            val definition = library.snapshot.first { it.ready }.conditions.firstOrNull { it.rule.id == ruleId } ?: return
            library.delete(workspace, PolicyRuleKind.NETWORK_CONDITION, ruleId.value, definition.revision)
        }
        override suspend fun setLastQuickAddProfile(profileId: NetworkConditionProfileId?) = library.updateSettings(workspace) { it.copy(quickAddProfileId = profileId) }
        override suspend fun resetShaping() = library.updateSettings(workspace) { it.copy(networkEnabled = false, profileId = null) }
    }

    private inner class ProtectionView(private val workspace: PolicyWorkspace) : ProtectedTrafficRepository {
        override fun forWorkspace(workspace: PolicyWorkspace): ProtectedTrafficRepository = protection(workspace)
        override val configuration: StateFlow<ProtectedTrafficConfiguration> = ProjectedStateFlow(library.snapshot) { it.protection(workspace) }
        override fun observeCatalog(): Flow<ProtectionPolicyCatalog> = library.snapshot.map { state ->
            ProtectionPolicyCatalog(workspace, state.workspaceNames[workspace] ?: "Saved device", state.protection(workspace),
                state.protectedRules.filter { it.visibleTo(workspace) }, state.ready, state.workspaceNames)
        }
        override suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean) = library.updateSettings(workspace) {
            it.copy(protectedAction = action, automaticTlsFallback = automaticFallbackEnabled)
        }
        override suspend fun upsertRule(rule: ProtectedTrafficRule) {
            val existing = library.snapshot.first { it.ready }.protectedRules.firstOrNull { it.rule.id == rule.id }
            if (existing != null && existing.rule.copy(enabled = rule.enabled) == rule)
                library.activate(workspace, PolicyRuleKind.PROTECTED_TRAFFIC, rule.id.value, rule.enabled)
            else library.saveProtection(workspace, rule, existing?.availability ?: RuleAvailability.Private(workspace), existing?.revision)
        }
        override suspend fun saveDefinition(rule: ProtectedTrafficRule, context: RuleEditContext) = library.saveProtection(
            context.workspace, rule, context.availability, context.expectedRevision, true, context.activateAfterSave, context.expectedUsage)
        override suspend fun deleteDefinition(id: ProtectedTrafficRuleId, context: RuleEditContext, acknowledgeSharedUsage: Boolean) =
            library.delete(context.workspace, PolicyRuleKind.PROTECTED_TRAFFIC, id.value, checkNotNull(context.expectedRevision), acknowledgeSharedUsage, context.expectedUsage)

        override suspend fun resetRule(id: ProtectedTrafficRuleId) {
            val definition = library.snapshot.first { it.ready }.protectedRules.firstOrNull { it.rule.id == id } ?: return
            if (definition.availability == RuleAvailability.Shared) library.activate(workspace, PolicyRuleKind.PROTECTED_TRAFFIC, id.value, false)
            else library.delete(workspace, PolicyRuleKind.PROTECTED_TRAFFIC, id.value, definition.revision)
        }
        override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) {
            val definition = library.snapshot.first { it.ready }.protectedRules.firstOrNull { it.rule.id == ruleId } ?: return
            library.delete(workspace, PolicyRuleKind.PROTECTED_TRAFFIC, ruleId.value, definition.revision)
        }
    }
}
