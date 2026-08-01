package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.engine.traffic.RegexCache

/**
 * Defines criteria to intercept and pause live HTTP requests or responses matching URL, method, and phase filters.
 *
 * @property id Unique identifier of the rule.
 * @property urlPattern A regular expression pattern to match against the full URL. Null matches any URL.
 * @property method HTTP method (e.g. GET, POST). Null matches any method.
 * @property phase Traffic phase filter (REQUEST, RESPONSE, BOTH).
 * @property enabled Whether this rule is actively evaluated.
 * @property priority Execution order priority (rules execute in ascending priority order).
 */
data class BreakpointRule(
    val id: String,
    val urlPattern: String? = null,
    val method: String? = null,
    val phase: BreakpointPhase = BreakpointPhase.BOTH,
    val enabled: Boolean = true,
    val priority: Int = 0
) {
    /**
     * Evaluates whether a target URL, method, and current traffic phase match this breakpoint rule.
     */
    fun matches(url: String, method: String, currentPhase: BreakpointPhase): Boolean {
        if (!enabled) return false

        // Phase check
        if (phase != BreakpointPhase.BOTH && phase != currentPhase) {
            return false
        }

        // Method check
        if (this.method != null && !this.method.equals(method, ignoreCase = true)) {
            return false
        }

        // URL Regex check
        if (!urlPattern.isNullOrBlank()) {
            val compiled = RegexCache.getOrNull(urlPattern)
            if (compiled != null && !compiled.containsMatchIn(url)) {
                return false
            }
        }

        return true
    }
}
