package com.devuloopers.knet.application.contract.device

import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.flow.StateFlow
import com.devuloopers.knet.companion.model.CompanionPolicyAcknowledgement

/** Last native application result, distinct from merely delivering a configuration frame. */
public data class DevicePolicyObservation(
    /** Revision acknowledged by this exact authenticated VPN generation. */
    public val revision: Long,
    /** Native policy owner's actual outcome. */
    public val result: CompanionPolicyAcknowledgement,
)

/** Current VPN presence, independent of retained capture and TCP connection lifetime. */
public enum class DevicePresenceStatus { OFFLINE, CONNECTED, RECONNECTING }

/** One verified runtime observation. No credential, source address or payload is retained. */
public data class DevicePresence(
    /** Status established by the current authenticated control generation. */
    public val status: DevicePresenceStatus,
    /** VPN-run identifier supplied inside an already authenticated channel. */
    public val runId: String,
    /** Desktop monotonic time of the latest application heartbeat. */
    public val lastHeartbeatMillis: Long,
    /** Most recent native policy result in this run, or null until acknowledged. */
    public val policy: DevicePolicyObservation? = null,
)

/** Read-only runtime boundary used by the device roster; online state is never restored from Room. */
public interface DevicePresenceReader {
    /** Bounded current/recent presence observations keyed by verified device identity. */
    public val presence: StateFlow<Map<RegisteredDeviceId, DevicePresence>>
}

/** Registered identity combined with its current control-channel health. */
public data class DeviceWorkspaceEntry(
    /** Durable identity and user-visible name. */
    public val device: RegisteredDevice,
    /** Offline until the current process verifies an active VPN run. */
    public val status: DevicePresenceStatus,
)
