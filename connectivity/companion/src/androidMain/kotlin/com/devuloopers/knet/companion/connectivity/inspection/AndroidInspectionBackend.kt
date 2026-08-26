package com.devuloopers.knet.companion.connectivity.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.model.CompanionFailure

/** Result returned by a qualified Android VPN packet backend. */
public sealed interface AndroidInspectionBackendResult {
    /** The native packet backend acquired its resources and started successfully. */
    public data object Started : AndroidInspectionBackendResult

    /** Native startup failed with a presentation-safe [failure]. */
    public data class Failed(public val failure: CompanionFailure) : AndroidInspectionBackendResult
}

/** Replaceable Android backend responsible for `VpnService`, TUN ownership, and bounded packet translation. */
public interface AndroidInspectionBackend {
    /** Starts packet handling for [configuration] after Android VPN consent has been confirmed. */
    public suspend fun start(configuration: CompanionInspectionConfiguration): AndroidInspectionBackendResult

    /** Stops packet handling and releases all native resources; repeated calls must be safe. */
    public suspend fun stop()
}
