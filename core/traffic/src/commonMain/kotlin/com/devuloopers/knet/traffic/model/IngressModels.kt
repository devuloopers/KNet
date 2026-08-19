package com.devuloopers.knet.traffic.model

/**
 * Stable source category describing how a client reached the proxy.
 */
public sealed interface IngressKind {
    /** Client connected directly from the local desktop host. */
    public data object Local : IngressKind

    /** Authenticated client connected through an explicitly enabled LAN binding. */
    public data object LanPairedDevice : IngressKind

    /** Stock client connected through KNet's open, exact-interface Wi-Fi proxy gateway. */
    public data object WifiLanClient : IngressKind

    /** Client reached the proxy through an application-owned ADB mapping. */
    public data object AdbDevice : IngressKind

    /** Paired companion connected through a direct tunnel. */
    public data object CompanionDirect : IngressKind

    /** Paired companion connected through an end-to-end encrypted relay tunnel. */
    public data object CompanionRelay : IngressKind

    /**
     * Preserves a future or product-specific ingress category.
     *
     * @property value Stable non-blank category token.
     */
    public data class Custom(public val value: String) : IngressKind {
        init {
            require(value.isNotBlank()) { "Custom ingress kind must not be blank." }
        }
    }
}

/**
 * Authorized logical identity of a client allowed to use the proxy.
 *
 * @property value Stable non-secret identity. Authentication credentials are never stored here.
 */
@JvmInline
public value class ClientIdentity(public val value: String) {
    init {
        require(value.isNotBlank()) { "ClientIdentity must not be blank." }
    }
}

/**
 * Neutral ingress metadata attached when a proxy connection is admitted.
 *
 * The traffic architecture can group and audit clients without knowing PAC, ADB commands,
 * companion tunnel implementations, or relay protocols.
 *
 * @property kind Typed ingress category.
 * @property clientIdentity Optional authorized client identity.
 */
public data class IngressContext(
    public val kind: IngressKind,
    public val clientIdentity: ClientIdentity? = null,
)

/** One-shot lookup used by a proxy listener to attribute an already-authorized bridge socket. */
public fun interface IngressAttributionLookup {
    /** Claims attribution for the downstream transport endpoint, or null for ordinary ingress. */
    public fun claim(downstream: TrafficEndpoint): IngressContext?
}

/** Registration side held only by authorized gateway/companion adapters. */
public fun interface IngressAttributionRegistration {
    /** Registers a short-lived one-shot attribution before connecting to the internal proxy. */
    public fun register(
        downstream: TrafficEndpoint,
        context: IngressContext,
        expiresAtEpochMillis: Long,
    ): Boolean
}
