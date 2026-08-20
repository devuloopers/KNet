package com.devuloopers.knet.engine.proxy

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies lock-free aggregation and runtime event-loop lag sampling. */
class ProxyRuntimeMetricsTest {

    @Test
    fun `lag aggregation reports sample count maximum and average`() {
        val metrics = ProxyRuntimeMetrics()
        metrics.recordEventLoopLagNanos(10L)
        metrics.recordEventLoopLagNanos(30L)

        val snapshot = metrics.snapshot()
        assertEquals(2L, snapshot.eventLoopLagSamples)
        assertEquals(30L, snapshot.maximumEventLoopLagNanos)
        assertEquals(20L, snapshot.averageEventLoopLagNanos)
    }

    @Test
    fun `running proxy samples event loop lag without traffic`() {
        val server = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
        )
        server.start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L)
            while (
                server.metricsSnapshot().eventLoopLagSamples == 0L &&
                System.nanoTime() < deadline
            ) {
                Thread.yield()
            }
            assertTrue(server.metricsSnapshot().eventLoopLagSamples > 0L)
        } finally {
            server.stop()
        }
    }

    /** Reserves an ephemeral loopback port. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }
}
