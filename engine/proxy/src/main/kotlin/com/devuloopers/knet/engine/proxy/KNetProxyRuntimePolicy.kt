package com.devuloopers.knet.engine.proxy

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Concrete transport limits applied by one [KNetProxyServer].
 *
 * @property connectTimeoutMillis Upstream TCP connect deadline.
 * @property tlsHandshakeTimeoutMillis Upstream and downstream TLS handshake deadline.
 * @property readIdleTimeoutMillis Socket read-idle deadline.
 * @property writeIdleTimeoutMillis Socket write-idle deadline.
 * @property gracefulShutdownTimeoutMillis Event-loop graceful shutdown deadline.
 * @property maximumDownstreamConnections Total admitted client connections.
 * @property maximumConnectionsPerClient Admitted connections for one remote address.
 * @property maximumUpstreamConnections Total concurrent origin connections.
 * @property maximumTlsClientHelloBytes Maximum buffered downstream TLS ClientHello size used for SNI selection.
 * @property maximumHttp2ConnectionsPerOrigin Maximum pooled HTTP/2 parent connections for one origin.
 * @property maximumHttp2ConcurrentStreams Maximum independently active streams per HTTP/2 connection.
 * @property maximumHttp2HeaderListBytes Maximum decoded header-list size accepted per stream.
 * @property http2InitialWindowBytes Initial per-stream receive window advertised to peers.
 */
data class KNetProxyRuntimePolicy(
    val connectTimeoutMillis: Long = 10_000L,
    val tlsHandshakeTimeoutMillis: Long = 10_000L,
    val readIdleTimeoutMillis: Long = 60_000L,
    val writeIdleTimeoutMillis: Long = 60_000L,
    val gracefulShutdownTimeoutMillis: Long = 5_000L,
    val maximumDownstreamConnections: Int = 1_024,
    val maximumConnectionsPerClient: Int = 128,
    val maximumUpstreamConnections: Int = 1_024,
    val maximumTlsClientHelloBytes: Int = 64 * 1024,
    val maximumHttp2ConnectionsPerOrigin: Int = 2,
    val maximumHttp2ConcurrentStreams: Long = 100L,
    val maximumHttp2HeaderListBytes: Long = 64L * 1024L,
    val http2InitialWindowBytes: Int = 65_535,
) {
    init {
        require(connectTimeoutMillis > 0L) { "Connect timeout must be positive." }
        require(tlsHandshakeTimeoutMillis > 0L) { "TLS handshake timeout must be positive." }
        require(readIdleTimeoutMillis > 0L) { "Read-idle timeout must be positive." }
        require(writeIdleTimeoutMillis > 0L) { "Write-idle timeout must be positive." }
        require(gracefulShutdownTimeoutMillis > 0L) { "Graceful shutdown timeout must be positive." }
        require(maximumDownstreamConnections > 0) { "Downstream connection limit must be positive." }
        require(maximumConnectionsPerClient > 0) { "Per-client connection limit must be positive." }
        require(maximumConnectionsPerClient <= maximumDownstreamConnections) {
            "Per-client connection limit cannot exceed the total downstream limit."
        }
        require(maximumUpstreamConnections > 0) { "Upstream connection limit must be positive." }
        require(maximumTlsClientHelloBytes > 0) { "TLS ClientHello limit must be positive." }
        require(maximumHttp2ConnectionsPerOrigin > 0) {
            "HTTP/2 connections-per-origin limit must be positive."
        }
        require(maximumHttp2ConnectionsPerOrigin <= maximumUpstreamConnections) {
            "HTTP/2 connections-per-origin limit cannot exceed the total upstream limit."
        }
        require(maximumHttp2ConcurrentStreams > 0L) { "HTTP/2 concurrent-stream limit must be positive." }
        require(maximumHttp2HeaderListBytes > 0L) { "HTTP/2 header-list limit must be positive." }
        require(http2InitialWindowBytes in 1..Int.MAX_VALUE) { "HTTP/2 initial window must be positive." }
    }
}

/**
 * Owns bounded downstream and upstream admission counters for one proxy runtime.
 *
 * Leases are idempotent so connect failures and channel-close callbacks cannot double-release a slot.
 * This is transport admission only; authenticated client authorization remains an application policy.
 *
 * @property policy Runtime limits enforced by the controller.
 */
class ProxyConnectionAdmissionController(
    private val policy: KNetProxyRuntimePolicy,
) {
    private val connectionsByClient = mutableMapOf<String, Int>()
    private var downstreamConnections: Int = 0
    private var upstreamConnections: Int = 0

    /**
     * Attempts to reserve one downstream connection for [clientKey].
     *
     * @return An idempotent lease, or `null` when either downstream limit is saturated.
     */
    @Synchronized
    fun tryAcquireDownstream(clientKey: String): ConnectionLease? {
        val clientConnections = connectionsByClient[clientKey] ?: 0
        if (
            downstreamConnections >= policy.maximumDownstreamConnections ||
            clientConnections >= policy.maximumConnectionsPerClient
        ) {
            return null
        }
        downstreamConnections += 1
        connectionsByClient[clientKey] = clientConnections + 1
        return ConnectionLease {
            releaseDownstream(clientKey)
        }
    }

    /**
     * Attempts to reserve one upstream origin connection.
     *
     * @return An idempotent lease, or `null` when the upstream limit is saturated.
     */
    @Synchronized
    fun tryAcquireUpstream(): ConnectionLease? {
        if (upstreamConnections >= policy.maximumUpstreamConnections) return null
        upstreamConnections += 1
        return ConnectionLease {
            releaseUpstream()
        }
    }

    /** Releases one downstream slot and removes an empty per-client counter. */
    @Synchronized
    private fun releaseDownstream(clientKey: String) {
        downstreamConnections = (downstreamConnections - 1).coerceAtLeast(0)
        val remaining = ((connectionsByClient[clientKey] ?: 1) - 1).coerceAtLeast(0)
        if (remaining == 0) {
            connectionsByClient.remove(clientKey)
        } else {
            connectionsByClient[clientKey] = remaining
        }
    }

    /** Releases one upstream slot. */
    @Synchronized
    private fun releaseUpstream() {
        upstreamConnections = (upstreamConnections - 1).coerceAtLeast(0)
    }
}

/**
 * Idempotent reservation returned by [ProxyConnectionAdmissionController].
 *
 * @property releaseAction Counter mutation executed at most once.
 */
class ConnectionLease(
    private val releaseAction: () -> Unit,
) : AutoCloseable {
    private val released = AtomicBoolean(false)

    /** Releases the reserved connection slot exactly once. */
    override fun close() {
        if (released.compareAndSet(false, true)) releaseAction()
    }
}
