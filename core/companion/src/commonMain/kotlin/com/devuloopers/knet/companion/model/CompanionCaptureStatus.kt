package com.devuloopers.knet.companion.model

import kotlinx.serialization.Serializable

/** Recording state for the authenticated device; independent of VPN connectivity and sibling capture choices. */
@Serializable
public enum class CompanionDesktopCaptureState { UNKNOWN, STOPPED, STARTING, PAUSED, CAPTURING }

/** Tiny versioned readiness response; no credentials, capture contents or sibling identities are exposed. */
public object CompanionCaptureStatusCodec {
    /** Encodes a complete authoritative state using a fixed bounded grammar. */
    public fun encode(state: CompanionDesktopCaptureState): ByteArray = "knet-capture-v1\n${state.name}\n".encodeToByteArray()
    /** Rejects unknown versions, malformed UTF-8, trailing fields and unknown states. */
    public fun decode(bytes: ByteArray): CompanionDesktopCaptureState {
        require(bytes.size in 1..64)
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        return CompanionDesktopCaptureState.entries.firstOrNull { text == "knet-capture-v1\n${it.name}\n" }
            ?: throw IllegalArgumentException("Invalid desktop capture status.")
    }
}
