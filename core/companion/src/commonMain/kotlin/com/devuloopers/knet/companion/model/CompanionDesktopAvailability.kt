package com.devuloopers.knet.companion.model

/** Authenticated reachability of the active paired desktop, independent from an inspection session. */
public sealed interface CompanionDesktopAvailability {
    /** Availability is not being observed because the operational Home screen is not active. */
    public data object Idle : CompanionDesktopAvailability

    /** The paired endpoint or a matching DNS-SD candidate is currently being authenticated. */
    public data class Checking(public val desktopId: CompanionDesktopId) : CompanionDesktopAvailability

    /** A pinned, credential-authenticated control request proved that the paired desktop is reachable. */
    public data class Available(
        public val desktopId: CompanionDesktopId,
        public val verifiedAtEpochMillis: Long,
    ) : CompanionDesktopAvailability {
        init {
            require(verifiedAtEpochMillis >= 0L) { "Desktop availability verification time must not be negative." }
        }
    }

    /** The paired desktop could not currently be reached for a recoverable reason. */
    public data class Unavailable(
        public val desktopId: CompanionDesktopId,
        public val reason: CompanionFailure,
    ) : CompanionDesktopAvailability {
        init {
            require(reason.recoverable) { "Unavailable desktop reasons must be recoverable." }
        }
    }

    /** Availability could not be established because a non-recoverable trust or identity failure occurred. */
    public data class Failed(
        public val desktopId: CompanionDesktopId,
        public val failure: CompanionFailure,
    ) : CompanionDesktopAvailability {
        init {
            require(!failure.recoverable) { "Terminal desktop availability failures must not be recoverable." }
        }
    }
}
