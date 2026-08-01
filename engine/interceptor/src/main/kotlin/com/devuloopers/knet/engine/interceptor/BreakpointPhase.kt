package com.devuloopers.knet.engine.interceptor

/**
 * Indicates which traffic phase (inbound request, outbound response, or both)
 * a breakpoint rule applies to.
 */
enum class BreakpointPhase {
    REQUEST,
    RESPONSE,
    BOTH
}
