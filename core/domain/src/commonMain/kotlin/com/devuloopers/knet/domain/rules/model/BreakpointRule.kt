package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.traffic.model.http.HttpMethod

/**
 * Canonical authored breakpoint rule shared by management, persistence, and interception.
 *
 * Transport matching is compiled by [BreakpointTransportMatcher]. Protocol-specific matching is
 * delegated through [protocolCriteria] to an application-registered extension.
 */
data class BreakpointRule(
    val id: String = "",
    val name: String = "",
    val urlPattern: String = "*",
    val method: HttpMethod? = null,
    val phase: BreakpointPhase = BreakpointPhase.BOTH,
    val enabled: Boolean = true,
    val protocolCriteria: ProtocolMatchCriteria = ProtocolMatchCriteria.HttpDefault,
) {
    init {
        require(id.isNotBlank()) { "Breakpoint rule ID must not be blank." }
        require(urlPattern.isNotBlank()) { "Breakpoint URL pattern must not be blank." }
    }
}

/**
 * Immutable compiled matcher for the HTTP transport portion of one [BreakpointRule].
 *
 * It deliberately knows nothing about GraphQL, gRPC, WebSocket, SSE, or other semantic
 * protocols. A coordinator can reuse this instance without recompiling the URL expression for
 * every intercepted exchange.
 */
class BreakpointTransportMatcher(private val rule: BreakpointRule) {
    private val methodToken = rule.method?.token
    private val urlExpression = compileUrlExpression(rule.urlPattern)

    /** Returns whether the rule includes the concrete interception [phase]. */
    fun includes(phase: BreakpointPhase): Boolean =
        rule.phase == BreakpointPhase.BOTH || rule.phase == phase

    /**
     * Evaluates only phase, HTTP method, and absolute URL.
     *
     * Protocol-specific criteria must be evaluated separately by the owning extension.
     */
    fun matches(url: String, method: String, phase: BreakpointPhase): Boolean =
        rule.enabled &&
            includes(phase) &&
            (methodToken == null || methodToken.equals(method, ignoreCase = true)) &&
            urlExpression.containsMatchIn(url)

    private companion object {
        fun compileUrlExpression(pattern: String): Regex {
            if (pattern == "*" || pattern == ".*") return Regex(".*", RegexOption.IGNORE_CASE)
            val expression = if ('*' in pattern && ".*" !in pattern) {
                pattern.split('*').joinToString(".*") { Regex.escape(it) }
            } else {
                pattern
            }
            return runCatching { Regex(expression, RegexOption.IGNORE_CASE) }
                .getOrElse { Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE) }
        }
    }
}
