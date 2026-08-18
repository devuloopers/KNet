package com.devuloopers.knet.domain.rules.model

/**
 * Represents the execution target phase for a network interception breakpoint rule.
 */
enum class BreakpointPhase {
    /**
     * Intercepts inbound HTTP client requests before reaching target server.
     */
    REQUEST,

    /**
     * Intercepts outbound HTTP server responses before returning to client.
     */
    RESPONSE,

    /**
     * Intercepts both inbound request and outbound response phases.
     */
    BOTH;

    companion object {
        /**
         * Safely parses string representation into [BreakpointPhase] with fallback to [BOTH].
         */
        fun fromString(value: String?): BreakpointPhase {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: BOTH
        }
    }
}
