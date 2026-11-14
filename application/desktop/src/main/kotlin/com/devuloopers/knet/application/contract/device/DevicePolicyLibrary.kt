package com.devuloopers.knet.application.contract.device

import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import kotlinx.coroutines.flow.StateFlow

/**
 * Durable execution settings; definitions remain shared assets independently of these values.
 * @property networkEnabled This workspace's simulator master switch.
 * @property profileId Reusable baseline profile, or null for no baseline shaping.
 * @property quickAddProfileId Last selected Traffic quick-add profile for this workspace.
 * @property interceptionEnabled Whether this workspace's active breakpoints may create new pauses.
 * @property protectedAction Default handling when no active explicit TLS rule matches.
 * @property automaticTlsFallback Whether explicit certificate rejection may authorize temporary later tunneling.
 */
public data class DevicePolicySettings(
    public val networkEnabled: Boolean = false,
    public val profileId: NetworkConditionProfileId? = null,
    public val quickAddProfileId: NetworkConditionProfileId? = null,
    public val interceptionEnabled: Boolean = true,
    public val protectedAction: ProtectedTrafficAction = ProtectedTrafficAction.INSPECT,
    public val automaticTlsFallback: Boolean = true,
)

/**
 * One transactionally consistent library snapshot, containing no credentials or captured bodies.
 * @property ready Whether committed storage has supplied a complete usable snapshot.
 * @property revision Monotonic committed publication sequence across repository writers.
 * @property workspaceNames Full identities mapped to display-only device names.
 * @property settings Persisted settings and safe defaults for registered workspaces.
 * @property profiles Shared custom profile definitions; built-ins remain owned by their registry.
 * @property breakpoints Authored Intercepts definitions and independent bindings.
 * @property conditions Authored network rules and independent bindings.
 * @property protectedRules Authored TLS rules and independent bindings.
 */
public data class DevicePolicyLibrarySnapshot(
    public val ready: Boolean = false,
    public val revision: Long = 0L,
    public val workspaceNames: Map<PolicyWorkspace, String> = emptyMap(),
    public val settings: Map<PolicyWorkspace, DevicePolicySettings> = emptyMap(),
    public val profiles: List<NetworkConditionProfile> = emptyList(),
    public val breakpoints: List<PolicyDefinition<BreakpointRule>> = emptyList(),
    public val conditions: List<PolicyDefinition<NetworkConditionRule>> = emptyList(),
    public val protectedRules: List<PolicyDefinition<ProtectedTrafficRule>> = emptyList(),
) {
    /** Settings for an unconfigured newly registered device use the safe product baseline. */
    public fun settingsFor(workspace: PolicyWorkspace): DevicePolicySettings = settings[workspace] ?: DevicePolicySettings()
    /** Library view; the enabled property is explicitly projected for this workspace. */
    public fun breakpointRules(workspace: PolicyWorkspace): List<BreakpointRule> = breakpoints.filter { it.visibleTo(workspace) }
        .map { it.rule.copy(enabled = it.activeOn(workspace)) }
    /** Configuration used both by this device's manager and actual transport. */
    public fun network(workspace: PolicyWorkspace): NetworkConditionConfiguration {
        val selected = settingsFor(workspace)
        return try { NetworkConditionConfiguration(enabled = ready && selected.networkEnabled,
            globalProfileId = selected.profileId, customProfiles = profiles,
            rules = conditions.filter { it.visibleTo(workspace) }.map { it.rule.copy(enabled = it.activeOn(workspace)) },
            lastQuickAddProfileId = selected.quickAddProfileId) }
        catch (_: IllegalArgumentException) {
            // Preserve malformed legacy rows for diagnosis while bypassing only the affected workspace's shaping.
            // Other devices' valid policies and TLS rules must remain available.
            NetworkConditionConfiguration()
        }
    }
    /** Minimal runtime snapshot; unrelated library edits cannot reset this workspace's queues/budgets. */
    public fun runtimeNetwork(workspace: PolicyWorkspace): NetworkConditionConfiguration {
        val view = network(workspace)
        val active = view.rules.filter { it.enabled }
        val used = active.mapTo(mutableSetOf()) { it.profileId }.also { view.globalProfileId?.let(it::add) }
        return view.copy(rules = active, customProfiles = view.customProfiles.filter { it.id in used }, lastQuickAddProfileId = null)
    }
    /** Manual TLS policy and defaults resolved independently of the selected desktop tab. */
    public fun protection(workspace: PolicyWorkspace): ProtectedTrafficConfiguration {
        val selected = settingsFor(workspace)
        return ProtectedTrafficConfiguration(defaultAction = selected.protectedAction,
            rules = protectedRules.filter { it.visibleTo(workspace) }.map { it.rule.copy(enabled = it.activeOn(workspace)) },
            configurationReady = ready, automaticTlsFallbackEnabled = selected.automaticTlsFallback)
    }
    /** Inactive library definitions have no effect on TLS learning or transport policy epochs. */
    public fun runtimeProtection(workspace: PolicyWorkspace): ProtectedTrafficConfiguration =
        protection(workspace).let { it.copy(rules = it.rules.filter { rule -> rule.enabled }) }
}

/**
 * Shared custom-profile references displayed before a deliberate global definition edit.
 * @property ruleCount All referencing definitions, including inactive definitions.
 * @property workspaceNames Workspaces using the profile through a baseline or active rule.
 */
public data class PolicyProfileUsage(public val ruleCount: Int = 0, public val workspaceNames: List<String> = emptyList())

/** Computes profile impact from one committed snapshot without reading captures or issuing per-row queries. */
public fun DevicePolicyLibrarySnapshot.profileUsage(id: NetworkConditionProfileId): PolicyProfileUsage {
    val referencing = conditions.filter { it.rule.profileId == id }
    val workspaces = settings.filterValues { it.profileId == id }.keys + referencing.flatMap { it.enabledOn }
    return PolicyProfileUsage(referencing.size, workspaces.map { workspaceNames[it] ?: "Saved device" }.distinct().sorted())
}

/** Atomic authored-definition and activation boundary. All mutations validate durable workspace ownership. */
public interface DevicePolicyLibrary {
    /** Runtime and presentation share this committed source of truth. */
    public val snapshot: StateFlow<DevicePolicyLibrarySnapshot>
    /** Saves a definition and automatically replaces exact matching activations only in [workspace]; returns the committed replacement IDs. */
    public suspend fun saveBreakpoint(workspace: PolicyWorkspace, rule: BreakpointRule, availability: RuleAvailability,
        expectedRevision: Long? = null, replaceEquivalent: Boolean = true, activateAfterSave: Boolean = false, expectedUsage: Set<PolicyWorkspace>? = null): RuleSaveResult
    /** Saves a destination/protocol rule with atomic device-local exact replacement and committed result. */
    public suspend fun saveCondition(workspace: PolicyWorkspace, rule: NetworkConditionRule, availability: RuleAvailability,
        expectedRevision: Long? = null, replaceEquivalent: Boolean = true, activateAfterSave: Boolean = false, expectedUsage: Set<PolicyWorkspace>? = null): RuleSaveResult
    /** Saves TLS handling with atomic device-local exact replacement; past capture evidence is immutable. */
    public suspend fun saveProtection(workspace: PolicyWorkspace, rule: ProtectedTrafficRule, availability: RuleAvailability,
        expectedRevision: Long? = null, replaceEquivalent: Boolean = true, activateAfterSave: Boolean = false, expectedUsage: Set<PolicyWorkspace>? = null): RuleSaveResult
    /** Enables/disables an available definition for one exact workspace. */
    public suspend fun activate(workspace: PolicyWorkspace, kind: PolicyRuleKind, id: String, enabled: Boolean, replaceEquivalent: Boolean = false)
    /** Deletes a definition and its bindings; in-use shared deletion requires explicit caller acknowledgement. */
    public suspend fun delete(workspace: PolicyWorkspace, kind: PolicyRuleKind, id: String, expectedRevision: Long, acknowledgeSharedUsage: Boolean = false, expectedUsage: Set<PolicyWorkspace>? = null)
    /** Atomically transforms one workspace's execution defaults. */
    public suspend fun updateSettings(workspace: PolicyWorkspace, transform: (DevicePolicySettings) -> DevicePolicySettings)
    /** Adds/edits a reusable custom profile. */
    public suspend fun saveProfile(profile: NetworkConditionProfile)
    /** Removes only unreferenced profiles. */
    public suspend fun deleteProfile(id: NetworkConditionProfileId): Boolean
    /** Atomically forgets registration/credentials and its private definitions, activations and preferences; captures and shared definitions survive. */
    public suspend fun forget(workspace: PolicyWorkspace)
}

/** Expected mutation failures are typed; adapters never require exception-message parsing. */
public enum class DevicePolicyFailure { WORKSPACE_REMOVED, PRIVATE_DEFINITION, STALE_REVISION, ACTIVE_EQUIVALENT, SHARED_IN_USE, MISSING_DEFINITION, INVALID_CONFIGURATION }

/** Sanitized policy editing failure suitable for a UI notice. */
public class DevicePolicyException(public val reason: DevicePolicyFailure) : IllegalStateException(when (reason) {
    DevicePolicyFailure.WORKSPACE_REMOVED -> "This device was removed. Reopen the editor for a saved device."
    DevicePolicyFailure.PRIVATE_DEFINITION -> "This definition belongs to another workspace."
    DevicePolicyFailure.STALE_REVISION -> "This definition changed. Reopen it before saving."
    DevicePolicyFailure.ACTIVE_EQUIVALENT -> "A matching rule is already enabled. Open that rule or create a device-specific copy."
    DevicePolicyFailure.SHARED_IN_USE -> "Other devices use this rule. Confirm its deletion for all affected devices, or disable it only for this device."
    DevicePolicyFailure.MISSING_DEFINITION -> "This definition was removed."
    DevicePolicyFailure.INVALID_CONFIGURATION -> "This configuration is invalid."
})
