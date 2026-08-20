package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficModifierIntegrationTest {

    @Test
    fun testPipelineInitializerIsOwnedByServerInstance() {
        val initializerInvoked = java.util.concurrent.atomic.AtomicBoolean(false)
        val customInitializer: (io.netty.channel.ChannelPipeline) -> Unit = { _ ->
            initializerInvoked.set(true)
        }
        val server = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
            pipelineInitializers = listOf(customInitializer),
        )
        try {
            server.start()
            java.net.Socket().use { socket ->
                socket.connect(server.boundAddress())
            }
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2L)
            while (!initializerInvoked.get() && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertTrue(initializerInvoked.get())
        } finally {
            server.stop()
        }
    }

    private fun availableLoopbackPort(): Int = java.net.ServerSocket().use { socket ->
        socket.bind(java.net.InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }
}
