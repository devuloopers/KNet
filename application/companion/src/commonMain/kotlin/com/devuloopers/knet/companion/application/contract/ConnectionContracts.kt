package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.flow.StateFlow

/**
 * Authenticated direct/relay carrier below VPN and protocol inspection.
 *
 * Implementations must verify the pinned desktop transport identity before sending credentials,
 * must not log credential values, and must make repeated connect/disconnect calls safe.
 */
public interface CompanionTransport {
    public val state: StateFlow<CompanionConnectionState>
    public suspend fun connect(registration: CompanionRegistration, credential: String): CompanionTransportResult
    public suspend fun disconnect()
}

/** Transport connection outcome. */
public sealed interface CompanionTransportResult {
    public data object Connected : CompanionTransportResult
    public data class Rejected(public val failure: CompanionFailure) : CompanionTransportResult
}

/** Current platform network availability. */
public fun interface CompanionNetworkObserver {
    public fun observe(): StateFlow<CompanionNetworkState>
}
