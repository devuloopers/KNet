package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDiscoveryCandidate
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import com.devuloopers.knet.companion.model.CompanionEndpointDescriptor
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import kotlinx.coroutines.flow.StateFlow

/** Native DNS-SD boundary; implementations must release platform browse resources from [stop]. */
public interface CompanionDesktopDiscovery {
    public val state: StateFlow<CompanionDiscoveryState>

    /** Begins browsing for advertisements matching at least one saved identity. */
    public fun start(targetDesktopIds: Set<CompanionDesktopId>)

    /** Stops browsing and returns to an idle state. Repeated calls must be safe. */
    public fun stop()
}

/** Authenticated endpoint reconciliation result for one untrusted discovery address. */
public sealed interface CompanionEndpointReconciliationResult {
    public data class Verified(public val descriptor: CompanionEndpointDescriptor) : CompanionEndpointReconciliationResult
    public data class Rejected(public val failure: CompanionFailure) : CompanionEndpointReconciliationResult
}

/** Performs pinned-TLS verification before accepting any DNS-SD supplied address. */
public fun interface CompanionEndpointReconciliationClient {
    public suspend fun reconcile(
        registration: CompanionRegistration,
        candidateEndpoint: CompanionServiceEndpoint,
        credential: String,
    ): CompanionEndpointReconciliationResult
}

/** Portable recovery result used by connection and continuous discovery orchestration. */
public sealed interface CompanionEndpointRecoveryResult {
    public data class Recovered(
        public val registration: CompanionRegistration,
        public val candidate: CompanionDiscoveryCandidate,
    ) : CompanionEndpointRecoveryResult
    public data class Rejected(public val failure: CompanionFailure) : CompanionEndpointRecoveryResult
}
