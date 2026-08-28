package com.devuloopers.knet.connectivity.desktop.gateway

import kotlinx.coroutines.flow.StateFlow

/** Strongly typed lifecycle exposed by the authenticated companion control listener. */
public sealed interface CompanionControlGatewayState {
    /** No listener has been requested, or the owner has stopped it. */
    public data object Stopped : CompanionControlGatewayState

    /** A listener socket is currently being created and bound. */
    public data object Starting : CompanionControlGatewayState

    /** The control endpoint is accepting connections on [port]. */
    public data class Listening(public val port: Int) : CompanionControlGatewayState {
        init {
            require(port in 1..65_535) { "Companion control gateway port is invalid." }
        }
    }

    /** The listener is unavailable and may be retried by its lifecycle owner. */
    public data class Failed(public val reason: CompanionControlGatewayFailure) : CompanionControlGatewayState
}

/** Stable failure categories used by lifecycle policy without exposing platform exceptions. */
public enum class CompanionControlGatewayFailure {
    BIND_FAILED,
    LISTENER_FAILED,
}

/** Restartable control-listener boundary consumed by process-owned lifecycle policy. */
public interface CompanionControlGatewayLifecycle : AutoCloseable {
    public val state: StateFlow<CompanionControlGatewayState>

    /** Attempts to start the listener synchronously; an unsuccessful attempt remains retryable. */
    public fun start()
}
