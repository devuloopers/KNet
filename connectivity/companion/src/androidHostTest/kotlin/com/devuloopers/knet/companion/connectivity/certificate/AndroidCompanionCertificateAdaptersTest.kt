package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.connectivity.http.AndroidCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.model.CompanionCertificateChallengeNonce
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.connectivity.testing.companionRegistrationFixture
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.pairing.DeviceScope
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AndroidCompanionCertificateAdaptersTest {
    @Test
    fun pinnedDownloadAcceptsOnlyTheRegisteredRootAndServingIdentity() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val registration = registration(root)
        val client = FakeTlsClient(
            pinned = AndroidCertificateTlsResult.Success(200, emptyMap(), root.encoded),
        )

        val result = assertIs<CompanionCertificateDownloadResult.Downloaded>(
            AndroidCompanionRootCertificateSource(client).download(registration, "credential"),
        )

        assertContentEquals(root.encoded, result.artifact.copyBytes())
        assertEquals(1, client.pinnedCalls)
    }

    @Test
    fun negotiatedPeerCannotBorrowAPinnedCertificateFromALaterChainEntry() {
        val root = testCertificate("local-proxy-test-ca.pem")
        val leaf = testCertificate("local-proxy-test-leaf.pem")

        val accepted = listOf(root, leaf).matchesPinnedTransportIdentity(leaf.encoded.sha256())

        assertEquals(false, accepted)
    }

    @Test
    fun platformTrustManagerAcceptsTheChainRootedInThePairedTrustStore() {
        val root = testCertificate("local-proxy-test-ca.pem")
        val leaf = testCertificate("local-proxy-test-leaf.pem")
        val pairedTrustManager = AndroidPairedTlsTrustFactory.trustManagers(root)
            .filterIsInstance<X509TrustManager>()
            .single()

        pairedTrustManager.checkServerTrusted(arrayOf(leaf, root), "RSA")
        assertFailsWith<IllegalArgumentException> {
            AndroidPairedTlsTrustFactory.trustManagers(leaf)
        }
    }

    @Test
    fun malformedInvitationRootIsRejectedBeforeOpeningTheControlConnection() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val registration = registration(root).copy(
            rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
        )

        val result = platformCertificateClient().executePinned(
            registration = registration,
            credential = "credential",
            path = CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH,
            challenge = null,
            maximumBodyBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
        )

        assertIs<AndroidCertificateTlsResult.IdentityRejected>(result)
    }

    @Test
    fun invitationRootFingerprintMismatchIsRejectedBeforeOpeningTheControlConnection() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val registration = registration(root).copy(
            rootCertificateSha256 = Sha256Fingerprint("0".repeat(64)),
        )

        val result = platformCertificateClient().executePinned(
            registration = registration,
            credential = "credential",
            path = CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH,
            challenge = null,
            maximumBodyBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
        )

        assertIs<AndroidCertificateTlsResult.IdentityRejected>(result)
    }

    @Test
    fun androidTrustRejectionMapsToInstallationRequired() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val registration = registration(root)
        val verifier = AndroidCompanionCertificateTrustVerifier(
            client = FakeTlsClient(platformTrusted = AndroidCertificateTlsResult.TrustRejected),
            nowEpochMillis = { 1_000L },
        )

        val result = verifier.verify(
            registration,
            "credential",
            CompanionCertificateArtifact(root.encoded, "knet-root-ca.crt"),
        )

        assertIs<CompanionCertificateState.InstallationRequired>(result)
    }

    @Test
    fun trustedResultRequiresTheExactEchoedNonce() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val registration = registration(root)
        val client = object : AndroidCertificateTlsClient {
            override suspend fun executePinned(
                registration: CompanionRegistration,
                credential: String,
                path: String,
                challenge: CompanionCertificateChallengeNonce?,
                maximumBodyBytes: Int,
            ): AndroidCertificateTlsResult = error("not used")

            override suspend fun executePlatformTrusted(
                registration: CompanionRegistration,
                credential: String,
                path: String,
                challenge: CompanionCertificateChallengeNonce,
                expectedRoot: X509Certificate,
                maximumBodyBytes: Int,
            ): AndroidCertificateTlsResult = AndroidCertificateTlsResult.Success(
                statusCode = 204,
                responseHeaders = mapOf(
                    CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase() to challenge.value,
                ),
                body = ByteArray(0),
            )
        }
        val verifier = AndroidCompanionCertificateTrustVerifier(client, nowEpochMillis = { 2_000L })

        val result = assertIs<CompanionCertificateState.Trusted>(
            verifier.verify(
                registration,
                "credential",
                CompanionCertificateArtifact(root.encoded, "knet-root-ca.crt"),
            ),
        )

        assertEquals(registration.rootCertificateSha256, result.rootCertificateSha256)
        assertEquals(2_000L, result.verifiedAtEpochMillis)
    }

    @Test
    fun mismatchedChallengeEchoIsRejected() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val verifier = AndroidCompanionCertificateTrustVerifier(
            client = FakeTlsClient(
                platformTrusted = AndroidCertificateTlsResult.Success(
                    statusCode = 204,
                    responseHeaders = mapOf(
                        CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase() to "z".repeat(43),
                    ),
                    body = ByteArray(0),
                ),
            ),
            nowEpochMillis = { 2_000L },
        )

        val result = verifier.verify(
            registration(root),
            "credential",
            CompanionCertificateArtifact(root.encoded, "knet-root-ca.crt"),
        )

        assertIs<CompanionCertificateState.Rejected>(result)
    }

    private class FakeTlsClient(
        private val pinned: AndroidCertificateTlsResult = AndroidCertificateTlsResult.Unavailable,
        private val platformTrusted: AndroidCertificateTlsResult = AndroidCertificateTlsResult.Unavailable,
    ) : AndroidCertificateTlsClient {
        var pinnedCalls: Int = 0

        override suspend fun executePinned(
            registration: CompanionRegistration,
            credential: String,
            path: String,
            challenge: CompanionCertificateChallengeNonce?,
            maximumBodyBytes: Int,
        ): AndroidCertificateTlsResult {
            pinnedCalls += 1
            return pinned
        }

        override suspend fun executePlatformTrusted(
            registration: CompanionRegistration,
            credential: String,
            path: String,
            challenge: CompanionCertificateChallengeNonce,
            expectedRoot: X509Certificate,
            maximumBodyBytes: Int,
        ): AndroidCertificateTlsResult = platformTrusted
    }

    private fun registration(root: X509Certificate): CompanionRegistration {
        val fingerprint = root.encoded.sha256()
        return companionRegistrationFixture(
            transportIdentitySha256 = fingerprint,
            rootCertificateSha256 = fingerprint,
            rootCertificateBytes = root.encoded,
            scopes = setOf(DeviceScope.PROXY_STREAM, DeviceScope.SETUP_ARTIFACT_READ),
        )
    }

    private fun platformCertificateClient(): PlatformAndroidCertificateTlsClient =
        PlatformAndroidCertificateTlsClient(
            KtorCompanionHttpClient(AndroidCompanionKtorClientProvider()),
        )

    private fun testCertificate(name: String): X509Certificate {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("tls/$name"))
        return stream.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte -> "%02x".format(byte) }
