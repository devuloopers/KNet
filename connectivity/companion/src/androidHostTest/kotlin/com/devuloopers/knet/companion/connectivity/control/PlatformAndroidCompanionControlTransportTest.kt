package com.devuloopers.knet.companion.connectivity.control

import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.connectivity.http.AndroidCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.certificate.sha256Hex
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import java.security.cert.CertificateFactory
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class PlatformAndroidCompanionControlTransportTest {
    @Test
    fun authenticatedAndroidRequestsUseTheCertificateDomainAsTheirActualAuthority() {
        val root = testCertificate("local-proxy-test-ca.pem")
        val handle = AndroidCompanionKtorClientProvider().create(
            CompanionHttpRequest(
                endpoint = CompanionServiceEndpoint("192.0.2.10", 8_183, secure = true),
                method = CompanionHttpMethod.GET,
                path = "/test",
                maximumResponseBytes = 0,
                security = CompanionHttpSecurity.PinnedRoot(
                    rootCertificate = CompanionRootCertificate(root.encoded),
                    rootCertificateSha256 = Sha256Fingerprint(root.sha256Hex()),
                    transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
                ),
            ),
        )

        try {
            assertEquals(CompanionCertificateProtocol.TLS_SERVER_NAME, handle.requestHost)
        } finally {
            handle.close()
        }
    }

    @Test
    fun platformCertificateValidationFailureMapsToTrustRejectedInsteadOfTransportUnavailable() {
        val root = testCertificate("local-proxy-test-ca.pem")
        val handle = AndroidCompanionKtorClientProvider().create(
            CompanionHttpRequest(
                endpoint = CompanionServiceEndpoint("192.0.2.10", 8_183, secure = true),
                method = CompanionHttpMethod.POST,
                path = CompanionCertificateProtocol.TRUST_CHALLENGE_PATH,
                maximumResponseBytes = 0,
                security = CompanionHttpSecurity.PlatformTrusted(
                    expectedRootCertificate = CompanionRootCertificate(root.encoded),
                    expectedRootCertificateSha256 = Sha256Fingerprint(root.sha256Hex()),
                    transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
                ),
            ),
        )

        try {
            assertIs<CompanionHttpSecurityException.TrustRejected>(
                handle.securityFailure(CertificateException("not installed")),
            )
        } finally {
            handle.close()
        }
    }

    @Test
    fun mismatchedRootPinIsRejectedBeforeOpeningTheControlConnection() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val request = CompanionControlRequest(
            endpoint = CompanionServiceEndpoint("127.0.0.1", 1, secure = true),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("0".repeat(64)),
            rootCertificate = CompanionRootCertificate(root.encoded),
            operation = CompanionControlOperation.PAIR,
            body = byteArrayOf(1),
        )

        val failure = assertFailsWith<CompanionHttpSecurityException.IdentityRejected> {
            transport().execute(request)
        }

        assertEquals("Companion TLS identity validation failed.", failure.message)
    }

    @Test
    fun matchingRootPassesLocalValidationBeforeNetworkAdmission() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val request = CompanionControlRequest(
            endpoint = CompanionServiceEndpoint("127.0.0.1", 1, secure = true),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint(root.sha256Hex()),
            rootCertificate = CompanionRootCertificate(root.encoded),
            operation = CompanionControlOperation.PAIR,
            body = byteArrayOf(1),
        )

        val failure = assertFailsWith<Throwable> {
            transport().execute(request)
        }

        kotlin.test.assertTrue(failure !is CompanionHttpSecurityException)
    }

    private fun transport(): KtorCompanionControlTransport = KtorCompanionControlTransport(
        KtorCompanionHttpClient(AndroidCompanionKtorClientProvider()),
    )

    private fun testCertificate(name: String): X509Certificate {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("tls/$name"))
        return stream.use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }
    }
}
