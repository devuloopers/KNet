package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyPipelineIntegrationTest {

    @Test
    fun testProxyServerStartStopLifecycle() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()
        val server = KNetProxyServer(
            port = 18088,
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(ca, cache),
        )

        assertFalse(server.isRunning())
        server.start()
        assertTrue(server.isRunning())
        server.stop()
        assertFalse(server.isRunning())
    }
}
