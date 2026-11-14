package com.devuloopers.knet.application.contract.networkconditions

import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.devuloopers.knet.domain.policy.*

/** Persistent source of the immutable Network Conditions snapshot consumed by data planes. */
public interface NetworkConditionsRepository {
    /** Deletes the definition with explicit shared-usage acknowledgement, rather than toggling a device. */
    public suspend fun deleteDefinition(id: NetworkConditionRuleId, context: RuleEditContext, acknowledgeSharedUsage: Boolean = false) {
        forWorkspace(context.workspace).deleteRule(id)
    }

    /** Atomic manager projection with availability, revisions and per-workspace activation. */
    public fun observeCatalog(): Flow<NetworkPolicyCatalog> = configuration.map { config ->
        NetworkPolicyCatalog(PolicyWorkspace.Other, "Other traffic", config, config.rules.map { rule ->
            PolicyDefinition(rule, RuleAvailability.Private(PolicyWorkspace.Other), 1L,
                if (rule.enabled) setOf(PolicyWorkspace.Other) else emptySet())
        })
    }

    /** Selects an immutable execution workspace, independent of later UI navigation. */
    public fun forWorkspace(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace): NetworkConditionsRepository {
        require(workspace == com.devuloopers.knet.domain.policy.PolicyWorkspace.Other)
        return this
    }
    /** Creates/edits availability and activates only the captured workspace. */
    public suspend fun saveDefinition(rule: NetworkConditionRule, context: com.devuloopers.knet.domain.policy.RuleEditContext): RuleSaveResult {
        require(context.workspace == com.devuloopers.knet.domain.policy.PolicyWorkspace.Other)
        upsertRule(rule)
        return RuleSaveResult()
    }
    public val configuration: StateFlow<NetworkConditionConfiguration>

    public suspend fun setEnabled(enabled: Boolean)
    public suspend fun setGlobalProfile(profileId: NetworkConditionProfileId?)

    /** Atomically selects a validated global default and enables shaping, preserving rules and custom profiles. */
    public suspend fun activateGlobalProfile(profileId: NetworkConditionProfileId?)

    public suspend fun upsertProfile(profile: NetworkConditionProfile)
    public suspend fun deleteProfile(profileId: NetworkConditionProfileId): Boolean
    public suspend fun upsertRule(rule: NetworkConditionRule)
    public suspend fun deleteRule(ruleId: NetworkConditionRuleId)
    public suspend fun setLastQuickAddProfile(profileId: NetworkConditionProfileId?)
    public suspend fun resetShaping()
}

public data class NetworkConditionRuntimeSnapshot(
    /** Monotonic timestamp for this aggregate sample. It is never an epoch timestamp. */
    public val sampledAtNanos: Long = 0L,
    public val activeFlows: Int = 0,
    public val queuedBytes: Long = 0L,
    /** Application payload bytes KNet has released upstream after any configured shaping delay. */
    public val uploadedBytes: Long = 0L,
    /** Application payload bytes KNet has released downstream after any configured shaping delay. */
    public val downloadedBytes: Long = 0L,
    public val delayedUnits: Long = 0L,
    public val faultedFlows: Long = 0L,
    public val appliedRuleIds: Set<NetworkConditionRuleId> = emptySet(),
)

/**
 * Read-only cumulative telemetry boundary; payloads and URLs never cross it.
 * Production engines publish on a subscriber-owned one-second clock, not per delayed chunk or fault.
 * Consumers derive rates from monotonic timestamps and must not treat extra observations as elapsed seconds.
 */
public interface NetworkConditionRuntimeTelemetry {
    public val snapshot: StateFlow<NetworkConditionRuntimeSnapshot>
    /** Scoped view over the existing single sample clock. */
    public fun observeWorkspace(workspace: PolicyWorkspace): Flow<NetworkConditionRuntimeSnapshot> {
        require(workspace == PolicyWorkspace.Other)
        return snapshot
    }
}

public enum class PacketConditionCapabilityState {
    AVAILABLE,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
}

public data class PacketConditionCapabilities(
    public val state: PacketConditionCapabilityState,
    public val supportsUdp: Boolean,
    public val supportsQuic: Boolean,
    public val supportsWebRtc: Boolean,
    public val detail: String,
)

/** Companion VPN/TUN capability and configuration boundary for UDP/QUIC/WebRTC shaping. */
public interface PacketConditionController {
    public val capabilities: StateFlow<PacketConditionCapabilities>
    public suspend fun apply(configuration: NetworkConditionConfiguration): Result<Unit>
    public suspend fun disable(): Result<Unit>
}

/** Consistent feature configuration and definition metadata for one editing workspace. */
public data class NetworkPolicyCatalog(
    public val workspace: PolicyWorkspace,
    public val workspaceName: String,
    public val configuration: NetworkConditionConfiguration,
    public val definitions: List<PolicyDefinition<NetworkConditionRule>>,
    public val ready: Boolean = true,
    /** Display-only workspace names for impact diagnostics. */
    public val workspaceNames: Map<PolicyWorkspace, String> = emptyMap(),
    /** Shared profile definition impact across all workspaces. */
    public val profileUsage: Map<NetworkConditionProfileId, com.devuloopers.knet.application.contract.device.PolicyProfileUsage> = emptyMap(),
)
