package com.devuloopers.knet.core.http.ssl

import com.devuloopers.knet.core.http.client.LocalProxyTlsTrust
import com.devuloopers.knet.core.http.client.PlatformHttpTrustManager
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlatformHttpTrustManagerTest {
    @Test
    fun strictPolicyUsesPlatformTrustRoots() {
        assertTrue(PlatformHttpTrustManager.get(verifySsl = true).acceptedIssuers.isNotEmpty())
    }

    @Test
    fun strictProxyPolicyAddsOnlyTheConfiguredLocalAuthority() {
        val certificateAuthority = loadCertificate(TEST_CA_RESOURCE)
        val leaf = loadCertificate(TEST_LEAF_RESOURCE)
        val chain = arrayOf(leaf, certificateAuthority)

        assertFailsWith<CertificateException> {
            PlatformHttpTrustManager.get(verifySsl = true)
                .checkServerTrusted(chain, RSA_AUTH_TYPE)
        }

        val proxyTrustManager = PlatformHttpTrustManager.get(
            verifySsl = true,
            localProxyTlsTrust = LocalProxyTlsTrust(certificateAuthority.encoded),
        )

        proxyTrustManager.checkServerTrusted(chain, RSA_AUTH_TYPE)
        assertTrue(
            proxyTrustManager.acceptedIssuers.any { issuer ->
                issuer.encoded.contentEquals(certificateAuthority.encoded)
            }
        )
    }

    @Test
    fun invalidLocalProxyAuthorityFailsClosed() {
        assertFailsWith<IllegalArgumentException> {
            PlatformHttpTrustManager.get(
                verifySsl = true,
                localProxyTlsTrust = LocalProxyTlsTrust(byteArrayOf(1, 2, 3)),
            )
        }
    }

    @Test
    fun localProxyTrustOwnsDefensiveCertificateCopies() {
        val expected = loadCertificate(TEST_CA_RESOURCE).encoded
        val supplied = expected.copyOf()
        val trust = LocalProxyTlsTrust(supplied)

        supplied.fill(0)
        val firstRead = trust.certificateAuthorityDerCopy()
        assertContentEquals(expected, firstRead)

        firstRead.fill(0)
        assertContentEquals(expected, trust.certificateAuthorityDerCopy())
    }

    @Test
    fun explicitInsecurePolicyAcceptsArbitraryChains() {
        val trustManager = PlatformHttpTrustManager.get(verifySsl = false)
        trustManager.checkServerTrusted(null, "RSA")
        trustManager.checkClientTrusted(null, "RSA")
        assertTrue(trustManager.acceptedIssuers.isEmpty())
    }

    /** Loads one deterministic PEM certificate used only by this trust-policy suite. */
    private fun loadCertificate(resourcePath: String): X509Certificate {
        val input = checkNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Missing TLS test certificate '$resourcePath'."
        }
        return input.use { stream ->
            CertificateFactory.getInstance(X509_CERTIFICATE_TYPE)
                .generateCertificate(stream) as X509Certificate
        }
    }

    private companion object {
        private const val TEST_CA_RESOURCE = "tls/local-proxy-test-ca.pem"
        private const val TEST_LEAF_RESOURCE = "tls/local-proxy-test-leaf.pem"
        private const val X509_CERTIFICATE_TYPE = "X.509"
        private const val RSA_AUTH_TYPE = "RSA"
    }
}
