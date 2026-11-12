package com.devuloopers.knet.ui.desktop.protectedtraffic.model

import com.devuloopers.knet.domain.protectedtraffic.*

/** Existing exact, wildcard and CIDR selectors displayed without rewriting their meaning. */
internal val ProtectedDestinationSelector.displayValue: String get() = when (this) {
    is ProtectedDestinationSelector.Exact -> value
    is ProtectedDestinationSelector.WildcardDomain -> "*.$suffix"
    is ProtectedDestinationSelector.Cidr -> "$networkAddress/$prefixLength"
}

/** The explicit desktop proxy retains historical Bypass as encrypted tunnelling. */
internal val ProtectedTrafficAction.displayLabel: String get() = when (this) {
    ProtectedTrafficAction.INSPECT -> "Inspect"
    ProtectedTrafficAction.TUNNEL -> "Tunnel"
    ProtectedTrafficAction.BLOCK -> "Block"
    ProtectedTrafficAction.BYPASS -> "Bypass (tunnel)"
}

/** Literal, case-insensitive search over existing selectors and policy metadata. */
internal val ProtectedTrafficRule.searchText: String get() = listOf(
    destination?.displayValue ?: "All destinations", sourceApplication?.value ?: "Any application",
    port?.toString() ?: "All ports", transport?.name ?: "All transports", action.displayLabel,
    ipFamily?.name ?: "Any IP", if (origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET) "Imported" else "",
).joinToString(" ")

/** Supported global choices; automatic fallback is not a manual rule action. */
internal val ProtectedTrafficDefaultHandling.displayLabel: String get() = when (this) {
    ProtectedTrafficDefaultHandling.INSPECT_WITH_FALLBACK -> "Inspect with automatic fallback"
    ProtectedTrafficDefaultHandling.INSPECT_ONLY -> "Inspect only"
    ProtectedTrafficDefaultHandling.TUNNEL -> "Tunnel"
    ProtectedTrafficDefaultHandling.BLOCK -> "Block"
}

/** Explains the actual TLS policy and its precedence without implying replay of a rejected connection. */
internal val ProtectedTrafficDefaultHandling.description: String get() {
    val behavior = when (this) {
        ProtectedTrafficDefaultHandling.INSPECT_WITH_FALLBACK ->
            "Inspect TLS traffic using KNet’s certificate. If the client explicitly rejects it, eligible later connections temporarily tunnel without decryption. The failed connection is not retried by KNet; the app must reconnect."
        ProtectedTrafficDefaultHandling.INSPECT_ONLY ->
            "Always attempt TLS inspection using KNet’s certificate. Certificate rejection fails the connection; automatic tunneling is disabled."
        ProtectedTrafficDefaultHandling.TUNNEL ->
            "Forward TLS traffic without decrypting it. KNet shows connection details, timing and transferred bytes; HTTP headers and bodies remain encrypted."
        ProtectedTrafficDefaultHandling.BLOCK ->
            "Reject TLS connections covered by this default."
    }
    return "$behavior\n\nSaved rules take priority. Changes apply to new connections."
}
