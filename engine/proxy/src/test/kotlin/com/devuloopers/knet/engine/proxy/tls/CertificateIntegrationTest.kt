package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CertificateIntegrationTest {

    @Test
    fun testCertificateGenerationAndCachingForProxy() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()

        val cert1 = cache.get("example.com", ca)
        val cert2 = cache.get("example.com", ca)

        assertNotNull(cert1)
        assertEquals(cert1, cert2)
    }
}
