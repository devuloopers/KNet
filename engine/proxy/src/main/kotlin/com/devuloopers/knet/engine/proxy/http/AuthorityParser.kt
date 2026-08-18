package com.devuloopers.knet.engine.proxy.http

/**
 * Parsed HTTP authority with a validated host and TCP port.
 *
 * @property host Host name or address without IPv6 brackets.
 * @property port Valid TCP port.
 */
internal data class ParsedAuthority(
    val host: String,
    val port: Int,
)

/** Stable reasons for rejecting an HTTP authority. */
internal enum class AuthorityRejection {
    EMPTY,
    INVALID_CHARACTER,
    INVALID_IPV6_LITERAL,
    INVALID_PORT,
}

/** Result of parsing an HTTP authority without throwing into a Netty pipeline. */
internal sealed interface AuthorityParseResult {
    /** Successfully validated authority. */
    data class Valid(val authority: ParsedAuthority) : AuthorityParseResult

    /** Rejected authority with a stable reason suitable for diagnostics. */
    data class Invalid(val reason: AuthorityRejection) : AuthorityParseResult
}

/**
 * Parses CONNECT targets and Host headers, including bracketed IPv6 literals.
 *
 * The parser rejects control characters, whitespace, user-info, paths, queries, and fragments so
 * an authority cannot smuggle another request component into DNS or connection establishment.
 */
internal object AuthorityParser {

    /**
     * Parses [value], applying [defaultPort] only when the authority does not contain a port.
     *
     * @return A validated authority or a typed rejection; this function never throws for input data.
     */
    fun parse(value: String, defaultPort: Int): AuthorityParseResult {
        require(defaultPort in 1..65_535) { "Default authority port must be between 1 and 65535." }
        if (value.isEmpty()) return AuthorityParseResult.Invalid(AuthorityRejection.EMPTY)
        if (value.any { it.isWhitespace() || it.isISOControl() } || value.any { it in "/@?#" }) {
            return AuthorityParseResult.Invalid(AuthorityRejection.INVALID_CHARACTER)
        }

        return if (value.startsWith('[')) {
            parseBracketedIpv6(value, defaultPort)
        } else {
            parseHostOrUnbracketedIpv6(value, defaultPort)
        }
    }

    /** Parses an RFC-style bracketed IPv6 authority with an optional port suffix. */
    private fun parseBracketedIpv6(value: String, defaultPort: Int): AuthorityParseResult {
        val closingBracket = value.indexOf(']')
        if (closingBracket <= 1) {
            return AuthorityParseResult.Invalid(AuthorityRejection.INVALID_IPV6_LITERAL)
        }

        val host = value.substring(1, closingBracket)
        val suffix = value.substring(closingBracket + 1)
        if (host.contains('[') || host.contains(']')) {
            return AuthorityParseResult.Invalid(AuthorityRejection.INVALID_IPV6_LITERAL)
        }
        if (suffix.isEmpty()) return valid(host, defaultPort)
        if (!suffix.startsWith(':') || suffix.length == 1) {
            return AuthorityParseResult.Invalid(AuthorityRejection.INVALID_IPV6_LITERAL)
        }

        return parsePort(host, suffix.substring(1))
    }

    /** Parses a host, a host-and-port pair, or an unbracketed IPv6 literal using the default port. */
    private fun parseHostOrUnbracketedIpv6(value: String, defaultPort: Int): AuthorityParseResult {
        return when (value.count { it == ':' }) {
            0 -> valid(value, defaultPort)
            1 -> {
                val separatorIndex = value.lastIndexOf(':')
                val host = value.substring(0, separatorIndex)
                val port = value.substring(separatorIndex + 1)
                parsePort(host, port)
            }
            else -> valid(value, defaultPort)
        }
    }

    /** Converts and validates an explicit TCP port. */
    private fun parsePort(host: String, portToken: String): AuthorityParseResult {
        val port = portToken.toIntOrNull()
            ?: return AuthorityParseResult.Invalid(AuthorityRejection.INVALID_PORT)
        if (port !in 1..65_535) return AuthorityParseResult.Invalid(AuthorityRejection.INVALID_PORT)
        return valid(host, port)
    }

    /** Builds a valid result after checking the host component. */
    private fun valid(host: String, port: Int): AuthorityParseResult {
        if (host.isEmpty()) return AuthorityParseResult.Invalid(AuthorityRejection.EMPTY)
        return AuthorityParseResult.Valid(ParsedAuthority(host = host, port = port))
    }
}
