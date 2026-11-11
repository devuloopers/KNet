package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.PacketConditionConfiguration

/** An authenticated immutable policy read; null means unavailable, never an instruction to disable immediately. */
public fun interface CompanionPacketPolicySource {
    /** Fetches one policy using this exact paired registration. Cancellation must propagate. */
    public suspend fun fetch(registration: CompanionRegistration): PacketConditionConfiguration?
}

/** Explicit acknowledgement from an existing native packet owner. */
public enum class PacketPolicyUpdateResult {
    /** The current owner applied this policy and replanned delayed work. */
    APPLIED,
    /** The owning VPN/provider session has ended. */
    INACTIVE,
    /** This peer/provider cannot apply policy in place. */
    UNSUPPORTED,
}

/** Native policy endpoint owned by one running VPN session, never by a connection or UI screen. */
public fun interface CompanionPacketPolicySink {
    /** Applies an authenticated snapshot without restarting the owner. */
    public suspend fun apply(configuration: PacketConditionConfiguration): PacketPolicyUpdateResult
}
