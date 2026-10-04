package com.devuloopers.knet.application.contract.networkconditions

import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import kotlinx.coroutines.flow.StateFlow

/** Persistent source of the immutable Network Conditions snapshot consumed by data planes. */
public interface NetworkConditionsRepository {
    public val configuration: StateFlow<NetworkConditionConfiguration>

    public suspend fun setEnabled(enabled: Boolean)
    public suspend fun setGlobalProfile(profileId: NetworkConditionProfileId?)
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

/** Read-only telemetry boundary; payloads and URLs never cross it. */
public interface NetworkConditionRuntimeTelemetry {
    public val snapshot: StateFlow<NetworkConditionRuntimeSnapshot>
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
