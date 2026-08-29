package com.devuloopers.knet.domain.rules.model

/** Strongly typed port constraint applied independently from a breakpoint URL expression. */
sealed interface BreakpointPortCriteria {
    /** Matches requests on every valid destination port. */
    data object Any : BreakpointPortCriteria

    /** Matches requests on one exact TCP destination port. */
    data class Exact(val value: Int) : BreakpointPortCriteria {
        init {
            require(value in MINIMUM_PORT..MAXIMUM_PORT) {
                "Breakpoint port must be between $MINIMUM_PORT and $MAXIMUM_PORT."
            }
        }
    }

    private companion object {
        const val MINIMUM_PORT: Int = 1
        const val MAXIMUM_PORT: Int = 65_535
    }
}

/** Complete request representations consumed by transport breakpoint matching. */
data class BreakpointTransportTarget(
    val canonicalUrl: String,
    val portlessUrl: String,
    val port: Int?,
) {
    init {
        require(canonicalUrl.isNotBlank()) { "Canonical breakpoint URL must not be blank." }
        require(portlessUrl.isNotBlank()) { "Portless breakpoint URL must not be blank." }
        require(port == null || port in 1..65_535) { "Breakpoint target port is invalid." }
    }
}
