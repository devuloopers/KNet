package com.devuloopers.knet.domain.protectedtraffic

/** Global TLS behavior; explicit rule actions remain Inspect, Tunnel, Block or legacy Bypass. */
enum class ProtectedTrafficDefaultHandling(
    /** Transport policy applied before any eligible automatic fallback. */
    val action: ProtectedTrafficAction,
    /** Whether confirmed certificate rejection may enable temporary passthrough. */
    val automaticFallback: Boolean,
) {
    /** Inspect first; eligible certificate rejection permits a temporary later tunnel. */
    INSPECT_WITH_FALLBACK(ProtectedTrafficAction.INSPECT, true),
    /** Inspect every matching connection without automatic passthrough. */
    INSPECT_ONLY(ProtectedTrafficAction.INSPECT, false),
    /** Preserve end-to-end TLS for unmatched connections. */
    TUNNEL(ProtectedTrafficAction.TUNNEL, false),
    /** Reject unmatched TLS connections. */
    BLOCK(ProtectedTrafficAction.BLOCK, false),
}

/** Maps persisted settings to the supported desktop choice without rewriting legacy Bypass records. */
val ProtectedTrafficConfiguration.defaultHandling: ProtectedTrafficDefaultHandling get() = when (defaultAction) {
    ProtectedTrafficAction.INSPECT -> if (automaticTlsFallbackEnabled) ProtectedTrafficDefaultHandling.INSPECT_WITH_FALLBACK
        else ProtectedTrafficDefaultHandling.INSPECT_ONLY
    ProtectedTrafficAction.TUNNEL, ProtectedTrafficAction.BYPASS -> ProtectedTrafficDefaultHandling.TUNNEL
    ProtectedTrafficAction.BLOCK -> ProtectedTrafficDefaultHandling.BLOCK
}
