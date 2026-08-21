package com.devuloopers.knet.traffic.model

/** Standard producers that can submit a request to KNet's capture pipeline. */
public enum class StandardTrafficOrigin(
    public val token: String,
    public val displayName: String,
) {
    PROXY_CLIENT("proxy-client", "Proxy client"),
    API_STUDIO("api-studio", "API Studio"),
}

/**
 * Identifies the feature or client that originated one captured exchange.
 *
 * This is deliberately independent from [IngressKind]. Ingress describes how the transport
 * reached the proxy, while origin describes which KNet feature (if any) initiated the exchange.
 */
public sealed interface TrafficOrigin {
    /** Stable token used for transport attribution and persistence. */
    public val token: String

    /** Human-readable fallback suitable for generic presentation. */
    public val displayName: String

    /** One origin from KNet's standard set. */
    public data class Standard(public val value: StandardTrafficOrigin) : TrafficOrigin {
        override val token: String = value.token
        override val displayName: String = value.displayName
    }

    /** Preserves an origin contributed by a future product or integration. */
    public data class Custom(override val token: String) : TrafficOrigin {
        init {
            require(token.isNotBlank()) { "Custom traffic origin must not be blank." }
        }

        override val displayName: String = token
            .split('-', '_')
            .filter(String::isNotBlank)
            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
            .ifBlank { token }
    }

    public companion object {
        public val ProxyClient: TrafficOrigin = Standard(StandardTrafficOrigin.PROXY_CLIENT)
        public val ApiStudio: TrafficOrigin = Standard(StandardTrafficOrigin.API_STUDIO)

        /** Restores a standard or extension origin from its stable token. */
        public fun fromToken(token: String): TrafficOrigin {
            require(token.isNotBlank()) { "Traffic origin must not be blank." }
            val standard = StandardTrafficOrigin.entries.firstOrNull {
                it.token.equals(token, ignoreCase = true)
            }
            return standard?.let(::Standard) ?: Custom(token)
        }
    }
}

/**
 * Reserved local-proxy attribution metadata shared by outbound clients and the proxy adapter.
 *
 * The proxy must consume this field before canonical header mapping and upstream forwarding. It
 * is diagnostic metadata, not an authentication mechanism and never belongs on the public wire.
 */
public object TrafficAttributionHeader {
    public const val NAME: String = "X-KNet-Internal-Origin"
}
