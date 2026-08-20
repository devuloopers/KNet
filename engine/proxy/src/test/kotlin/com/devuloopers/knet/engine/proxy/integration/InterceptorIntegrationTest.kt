package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import org.junit.Assert.assertNotNull
import org.junit.Test

class InterceptorIntegrationTest {

    @Test
    fun testInterceptorPipelineIntegrationSetup() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()
        val server = KNetProxyServer(
            port = 18090,
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(ca, cache),
        )

        assertNotNull(server)
    }
}
