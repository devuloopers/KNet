package com.devuloopers.knet.engine.proxy.migration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import org.junit.Assert.assertNotNull
import org.junit.Test

class MigrationRegressionTest {

    @Test
    fun testProxyPublicApiCompatibility() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()
        val server = KNetProxyServer(port = 18099, ca = ca, certCache = cache)

        assertNotNull(server)
    }
}
