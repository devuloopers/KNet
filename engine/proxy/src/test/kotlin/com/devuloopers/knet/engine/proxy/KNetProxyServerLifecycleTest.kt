package com.devuloopers.knet.engine.proxy

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression tests for safe proxy listener defaults and atomic lifecycle cleanup. */
class KNetProxyServerLifecycleTest {

    /** Verifies the default listener is reachable only through loopback. */
    @Test
    fun `default listener binds to loopback`() {
        val server = createServer(availableLoopbackPort())

        try {
            server.start()

            assertTrue(server.isRunning())
            assertEquals(KNetProxyServer.DEFAULT_BIND_HOST, server.boundAddress()?.address?.hostAddress)
        } finally {
            server.stop()
        }
    }

    /** Verifies a failed bind rolls back state and permits a clean retry on the same instance. */
    @Test
    fun `failed bind rolls back resources and allows retry`() {
        val blocker = ServerSocket()
        blocker.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        val port = blocker.localPort
        val server = createServer(port)

        assertFailsWith<Exception> { server.start() }
        assertFalse(server.isRunning())
        assertEquals(null, server.boundAddress())

        blocker.close()
        try {
            server.start()
            assertTrue(server.isRunning())
        } finally {
            server.stop()
        }

        assertFalse(server.isRunning())
        assertEquals(null, server.boundAddress())
    }

    /** Creates a production-configured proxy runtime for lifecycle tests. */
    private fun createServer(port: Int): KNetProxyServer {
        return KNetProxyServer(
            port = port,
            serverTlsContextProvider = TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
        )
    }

    /** Reserves and releases a loopback port for a subsequent bind. */
    private fun availableLoopbackPort(): Int {
        return ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            socket.localPort
        }
    }
}
