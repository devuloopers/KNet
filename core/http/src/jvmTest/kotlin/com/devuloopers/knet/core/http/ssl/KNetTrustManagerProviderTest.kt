package com.devuloopers.knet.core.http.ssl

import com.devuloopers.knet.engine.certificate.ssl.KNetTrustManagerProvider
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KNetTrustManagerProviderTest {

    @Test
    fun testGetX509TrustManagerWithVerifySslEnabled() {
        val trustManager = KNetTrustManagerProvider.getX509TrustManager(verifySsl = true)
        assertNotNull(trustManager)
        assertTrue(trustManager.acceptedIssuers.isNotEmpty(), "Accepted issuers should contain root CA certificates")
    }

    @Test
    fun testGetX509TrustManagerWithVerifySslDisabled() {
        val trustManager = KNetTrustManagerProvider.getX509TrustManager(verifySsl = false)
        assertNotNull(trustManager)
        
        // Permissive manager should not throw exception on null/arbitrary certificates
        trustManager.checkServerTrusted(null, "RSA")
        trustManager.checkClientTrusted(null, "RSA")
    }

    @Test
    fun testGetTrustManagerFactoryWithVerifySslEnabled() {
        val factory = KNetTrustManagerProvider.getTrustManagerFactory(verifySsl = true)
        assertNotNull(factory)
        val trustManagers = factory.trustManagers
        assertTrue(trustManagers.isNotEmpty())
        assertTrue(trustManagers.any { it is X509TrustManager })
    }

    @Test
    fun testGetTrustManagerFactoryWithVerifySslDisabled() {
        val factory = KNetTrustManagerProvider.getTrustManagerFactory(verifySsl = false)
        assertNotNull(factory)
        val trustManagers = factory.trustManagers
        assertTrue(trustManagers.isNotEmpty())
    }
}
