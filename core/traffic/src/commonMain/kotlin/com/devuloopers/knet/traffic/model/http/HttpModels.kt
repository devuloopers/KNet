package com.devuloopers.knet.traffic.model.http

/**
 * Strongly typed HTTP header name while preserving the original captured spelling.
 *
 * @property value Non-blank header name as observed or authored.
 */
@JvmInline
public value class HeaderName(public val value: String) {
    init {
        require(value.isNotBlank()) { "HeaderName must not be blank." }
    }
}

/**
 * One ordered HTTP header field.
 *
 * Repeated fields remain separate list entries so values such as `Set-Cookie` are never
 * collapsed by the canonical traffic model.
 *
 * @property name Header field name.
 * @property value Header field value exactly as represented at this semantic boundary.
 */
public data class HeaderField(
    public val name: HeaderName,
    public val value: String,
)

/**
 * Standard request methods recognized by KNet.
 *
 * Unknown extension methods are represented by [HttpMethod.Custom].
 */
public enum class StandardHttpMethod(public val token: String) {
    GET("GET"),
    HEAD("HEAD"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE"),
    CONNECT("CONNECT"),
}

/**
 * HTTP request method with typed standard values and an extension-safe custom value.
 */
public sealed interface HttpMethod {
    /** Wire token used for serialization and display. */
    public val token: String

    /**
     * Wraps a method from the standard KNet method set.
     *
     * @property value Standard method value.
     */
    public data class Standard(public val value: StandardHttpMethod) : HttpMethod {
        override val token: String = value.token
    }

    /**
     * Preserves an extension method unknown to the standard set.
     *
     * @property token Non-blank wire token.
     */
    public data class Custom(override val token: String) : HttpMethod {
        init {
            require(token.isNotBlank()) { "Custom HTTP method must not be blank." }
            require(token.all(Char::isHttpTokenCharacter)) {
                "Custom HTTP method must contain only valid HTTP token characters."
            }
        }
    }

    public companion object {
        public val GET: HttpMethod = Standard(StandardHttpMethod.GET)
        public val HEAD: HttpMethod = Standard(StandardHttpMethod.HEAD)
        public val POST: HttpMethod = Standard(StandardHttpMethod.POST)
        public val PUT: HttpMethod = Standard(StandardHttpMethod.PUT)
        public val PATCH: HttpMethod = Standard(StandardHttpMethod.PATCH)
        public val DELETE: HttpMethod = Standard(StandardHttpMethod.DELETE)
        public val OPTIONS: HttpMethod = Standard(StandardHttpMethod.OPTIONS)
        public val TRACE: HttpMethod = Standard(StandardHttpMethod.TRACE)
        public val CONNECT: HttpMethod = Standard(StandardHttpMethod.CONNECT)

        /**
         * Creates a typed method from a wire token.
         *
         * @param token Method token to normalize against the standard set.
         * @return A standard method when recognized, otherwise a custom method preserving the token.
         * @throws IllegalArgumentException When [token] is blank.
         */
        public fun fromToken(token: String): HttpMethod {
            require(token.isNotBlank()) { "HTTP method must not be blank." }
            val normalized = token.uppercase()
            val standard = StandardHttpMethod.entries.firstOrNull { it.token == normalized }
            return standard?.let(::Standard) ?: Custom(token)
        }
    }
}

private fun Char.isHttpTokenCharacter(): Boolean =
    this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this in "!#$%&'*+-.^_`|~"

/**
 * Standard application protocol versions understood by the shared traffic model.
 */
public enum class StandardApplicationProtocol(public val token: String) {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
    HTTP_2("HTTP/2"),
    HTTP_3("HTTP/3"),
}

/**
 * Application protocol with typed HTTP versions and an extension-safe custom value.
 */
public sealed interface ApplicationProtocol {
    /** Stable token suitable for storage, export, and display. */
    public val token: String

    /**
     * Wraps a standard protocol version.
     *
     * @property value Standard protocol value.
     */
    public data class Standard(public val value: StandardApplicationProtocol) : ApplicationProtocol {
        override val token: String = value.token
    }

    /**
     * Preserves a protocol token not yet modeled by KNet.
     *
     * @property token Non-blank protocol token.
     */
    public data class Custom(override val token: String) : ApplicationProtocol {
        init {
            require(token.isNotBlank()) { "Custom application protocol must not be blank." }
        }
    }

    public companion object {
        /**
         * Creates a typed application protocol from a persisted or observed token.
         *
         * @param token Protocol token to normalize against the standard set.
         * @return A standard protocol when recognized, otherwise a custom protocol preserving [token].
         * @throws IllegalArgumentException When [token] is blank.
         */
        public fun fromToken(token: String): ApplicationProtocol {
            require(token.isNotBlank()) { "Application protocol must not be blank." }
            val standard = StandardApplicationProtocol.entries.firstOrNull {
                it.token.equals(token, ignoreCase = true)
            }
            return standard?.let(::Standard) ?: Custom(token)
        }
    }
}

/**
 * Standard URI schemes used by the HTTP proxy and API Studio.
 */
public enum class StandardHttpScheme(public val token: String) {
    HTTP("http"),
    HTTPS("https"),
}

/**
 * HTTP request scheme with support for explicitly preserved extension schemes.
 */
public sealed interface HttpScheme {
    /** Normalized or preserved scheme token. */
    public val token: String

    /**
     * Wraps a standard HTTP scheme.
     *
     * @property value Standard scheme value.
     */
    public data class Standard(public val value: StandardHttpScheme) : HttpScheme {
        override val token: String = value.token
    }

    /**
     * Preserves a non-standard scheme.
     *
     * @property token Non-blank scheme token.
     */
    public data class Custom(override val token: String) : HttpScheme {
        init {
            require(token.isNotBlank()) { "Custom HTTP scheme must not be blank." }
        }
    }

    public companion object {
        /**
         * Creates a typed scheme from a persisted or observed token.
         *
         * @param token Scheme token to normalize against the standard set.
         * @return A standard scheme when recognized, otherwise a custom scheme preserving [token].
         * @throws IllegalArgumentException When [token] is blank.
         */
        public fun fromToken(token: String): HttpScheme {
            require(token.isNotBlank()) { "HTTP scheme must not be blank." }
            val standard = StandardHttpScheme.entries.firstOrNull {
                it.token.equals(token, ignoreCase = true)
            }
            return standard?.let(::Standard) ?: Custom(token)
        }
    }
}

/**
 * Validated network authority shared by proxy, API Studio, connectivity, and presentation.
 *
 * IPv6 bracket normalization belongs to the authority parser/formatter, while this value stores
 * the semantic host without making any JVM networking call.
 *
 * @property host Non-blank DNS name or IP literal.
 * @property port Optional TCP port in the valid user-addressable range.
 */
public data class Authority(
    public val host: String,
    public val port: Int? = null,
) {
    init {
        require(host.isNotBlank()) { "Authority host must not be blank." }
        require(port == null || port in 1..65_535) { "Authority port must be between 1 and 65535." }
    }
}

/**
 * Semantic HTTP request target without a dependency on `java.net.URI`.
 */
public sealed interface RequestTarget {
    /**
     * Absolute-form target normally used for a request sent to an explicit proxy.
     *
     * @property scheme Request scheme.
     * @property authority Destination authority.
     * @property pathAndQuery Raw path and optional query, beginning with `/`.
     */
    public data class Absolute(
        public val scheme: HttpScheme,
        public val authority: Authority,
        public val pathAndQuery: String,
    ) : RequestTarget {
        init {
            require(pathAndQuery.startsWith('/')) { "Absolute target path must begin with '/'." }
        }
    }

    /**
     * Origin-form target used for ordinary HTTP requests after routing is known.
     *
     * @property pathAndQuery Raw path and optional query, beginning with `/`.
     */
    public data class Origin(public val pathAndQuery: String) : RequestTarget {
        init {
            require(pathAndQuery.startsWith('/')) { "Origin target path must begin with '/'." }
        }
    }

    /**
     * Authority-form target used by HTTP CONNECT.
     *
     * @property authority Target authority.
     */
    public data class AuthorityForm(public val authority: Authority) : RequestTarget

    /** Asterisk-form target used by server-wide OPTIONS requests. */
    public data object Asterisk : RequestTarget

    /**
     * Losslessly preserves an extension target that cannot be represented by a standard form.
     *
     * This value is suitable for inspection. A request executor must validate and
     * deliberately translate it before writing anything to a network channel.
     *
     * @property value Non-blank target value without carriage-return or line-feed characters.
     */
    public data class Custom(public val value: String) : RequestTarget {
        init {
            require(value.isNotBlank()) { "Custom request target must not be blank." }
            require('\r' !in value && '\n' !in value) {
                "Custom request target must not contain line breaks."
            }
        }
    }
}

/**
 * Validated HTTP response status code.
 *
 * @property code Three-digit HTTP status code.
 */
@JvmInline
public value class HttpStatus(public val code: Int) {
    init {
        require(code in 100..999) { "HTTP status code must be between 100 and 999." }
    }
}

/**
 * Immutable semantic request metadata shared by all KNet features.
 *
 * @property method Typed request method.
 * @property target Typed request target.
 * @property protocol Observed or intended application protocol.
 * @property headers Ordered header fields with duplicates preserved.
 */
public data class RequestHead(
    public val method: HttpMethod,
    public val target: RequestTarget,
    public val protocol: ApplicationProtocol,
    public val headers: List<HeaderField>,
)

/**
 * Immutable semantic response metadata shared by all KNet features.
 *
 * @property protocol Observed application protocol.
 * @property status Typed response status.
 * @property reasonPhrase Optional observed reason phrase; HTTP/2 and HTTP/3 normally omit it.
 * @property headers Ordered header fields with duplicates preserved.
 */
public data class ResponseHead(
    public val protocol: ApplicationProtocol,
    public val status: HttpStatus,
    public val reasonPhrase: String? = null,
    public val headers: List<HeaderField>,
)
