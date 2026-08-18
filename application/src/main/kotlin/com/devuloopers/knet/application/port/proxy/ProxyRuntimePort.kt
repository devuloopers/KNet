package com.devuloopers.knet.application.port.proxy

import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Requested listener binding supplied to a proxy runtime implementation.
 *
 * @property host Explicit host or address to bind; no wildcard is inferred by the runtime.
 * @property port Requested TCP port.
 * @property scope Exposure scope used by access policy and connectivity descriptors.
 */
public data class ProxyBindingConfiguration(
    public val host: String,
    public val port: Int,
    public val scope: ProxyEndpointScope,
) {
    init {
        require(host.isNotBlank()) { "Proxy binding host must not be blank." }
        require(port in 1..65_535) { "Proxy binding port must be between 1 and 65535." }
    }
}

/**
 * Timeout policy for one proxy runtime instance.
 *
 * @property connectMillis Upstream connection deadline.
 * @property tlsHandshakeMillis Upstream or downstream TLS handshake deadline.
 * @property readIdleMillis Inbound read-idle deadline.
 * @property writeIdleMillis Outbound write-idle deadline.
 * @property gracefulShutdownMillis Maximum graceful shutdown duration.
 */
public data class ProxyTimeoutPolicy(
    public val connectMillis: Long,
    public val tlsHandshakeMillis: Long,
    public val readIdleMillis: Long,
    public val writeIdleMillis: Long,
    public val gracefulShutdownMillis: Long,
) {
    init {
        val values = listOf(
            connectMillis,
            tlsHandshakeMillis,
            readIdleMillis,
            writeIdleMillis,
            gracefulShutdownMillis,
        )
        require(values.all { it > 0L }) { "Proxy timeout values must be positive." }
    }
}

/**
 * Bounded connection policy for one proxy runtime instance.
 *
 * @property maximumDownstreamConnections Total admitted downstream connection limit.
 * @property maximumConnectionsPerClient Per-client admitted connection limit.
 * @property maximumUpstreamConnections Total upstream connection limit.
 */
public data class ProxyConnectionLimits(
    public val maximumDownstreamConnections: Int,
    public val maximumConnectionsPerClient: Int,
    public val maximumUpstreamConnections: Int,
) {
    init {
        require(maximumDownstreamConnections > 0) { "Downstream connection limit must be positive." }
        require(maximumConnectionsPerClient > 0) { "Per-client connection limit must be positive." }
        require(maximumUpstreamConnections > 0) { "Upstream connection limit must be positive." }
    }
}

/**
 * Technology-neutral configuration accepted by a proxy runtime adapter.
 *
 * @property bindings Explicit listener bindings.
 * @property verifyUpstreamTls Whether remote peer certificates must be verified.
 * @property timeouts Phase-specific timeout policy.
 * @property connectionLimits Bounded downstream/upstream connection policy.
 */
public data class ProxyRuntimeConfiguration(
    public val bindings: List<ProxyBindingConfiguration>,
    public val verifyUpstreamTls: Boolean,
    public val timeouts: ProxyTimeoutPolicy,
    public val connectionLimits: ProxyConnectionLimits,
) {
    init {
        require(bindings.isNotEmpty()) { "At least one proxy binding is required." }
    }
}

/**
 * Stable runtime handle published only after every listener and required service starts successfully.
 *
 * @property runtimeId Non-secret identifier for correlation and idempotent lifecycle commands.
 * @property endpoints Versioned bound endpoints consumed by connectivity mechanisms.
 */
public data class ProxyRuntimeHandle(
    public val runtimeId: String,
    public val endpoints: ProxyEndpointSnapshot,
) {
    init {
        require(runtimeId.isNotBlank()) { "Proxy runtime identifier must not be blank." }
    }
}

/**
 * Serialized lifecycle state exposed by a proxy runtime adapter.
 */
public sealed interface ProxyRuntimeState {
    /** Proxy owns no listener or connection resource. */
    public data object Stopped : ProxyRuntimeState

    /** Proxy is allocating resources that are not yet externally published. */
    public data object Starting : ProxyRuntimeState

    /**
     * Proxy is accepting traffic through a fully initialized handle.
     *
     * @property handle Published runtime handle.
     */
    public data class Running(public val handle: ProxyRuntimeHandle) : ProxyRuntimeState

    /** Proxy is rejecting new work and closing owned resources. */
    public data object Stopping : ProxyRuntimeState

    /**
     * Startup or runtime lifecycle failed after cleanup was attempted.
     *
     * @property code Stable safe failure code.
     * @property recoverable Whether a new start may be attempted without external repair.
     */
    public data class Failed(
        public val code: String,
        public val recoverable: Boolean,
    ) : ProxyRuntimeState
}

/**
 * Reason supplied to an explicit proxy stop operation.
 */
public enum class ProxyStopReason {
    USER_REQUEST,
    APPLICATION_SHUTDOWN,
    CONFIGURATION_CHANGED,
    SECURITY_REVOKED,
}

/**
 * Result of starting a proxy runtime.
 */
public sealed interface ProxyStartResult {
    /**
     * Runtime is active.
     *
     * @property handle Fully initialized runtime handle.
     */
    public data class Running(public val handle: ProxyRuntimeHandle) : ProxyStartResult

    /**
     * Runtime could not start and rolled back allocated resources.
     *
     * @property code Stable safe failure code.
     */
    public data class Failed(public val code: String) : ProxyStartResult
}

/**
 * Result of stopping a proxy runtime.
 */
public sealed interface ProxyStopResult {
    /** Runtime is fully stopped. */
    public data object Stopped : ProxyStopResult

    /**
     * Shutdown reached its deadline and forced one or more resources closed.
     *
     * @property resourceCodes Stable resource identifiers that required forced closure.
     */
    public data class Forced(public val resourceCodes: List<String>) : ProxyStopResult
}

/**
 * Application port implemented by the concrete Netty proxy runtime.
 *
 * Implementations serialize lifecycle operations, publish handles only after complete startup,
 * and own every listener, channel, event loop, resolver, and worker until [stop] completes.
 */
public interface ProxyRuntimePort {
    /** Current serialized lifecycle state. */
    public val state: StateFlow<ProxyRuntimeState>

    /**
     * Starts the proxy or returns the idempotent current running handle.
     *
     * @param configuration Validated technology-neutral runtime configuration.
     * @return Running handle or typed startup failure after rollback.
     */
    public suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult

    /**
     * Stops the proxy and awaits resource closure within the configured deadline.
     *
     * @param reason Reason for audit and connection-drain policy.
     * @return Stopped or forced-close result.
     */
    public suspend fun stop(reason: ProxyStopReason): ProxyStopResult
}
