package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.connectivity.http.AndroidCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.testing.companionRegistrationFixture
import com.devuloopers.knet.companion.model.CompanionCertificateChallengeNonce
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailureCode
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
            pinned = AndroidCertificateTlsResult.Success(
                200,
                mapOf("content-type" to CompanionCertificateProtocol.ROOT_CERTIFICATE_MEDIA_TYPE),
                root.encoded,
            ),
        )

        val result = assertIs<CompanionCertificateDownloadResult.Downloaded>(
            AndroidCompanionRootCertificateSource(client).download(registration, "credential"),
        )

        assertContentEquals(root.encoded, result.artifact.copyBytes())
        assertEquals(CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH, client.lastPinnedPath)
        assertEquals("knet-root-ca.crt", result.artifact.suggestedFileName)
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
            trustedCertificates = FakeTrustedCertificateStore(AndroidTrustedCertificateLookupResult.Present),
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
        val verifier = AndroidCompanionCertificateTrustVerifier(
            client = client,
            trustedCertificates = FakeTrustedCertificateStore(AndroidTrustedCertificateLookupResult.Present),
            nowEpochMillis = { 2_000L },
        )

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
            trustedCertificates = FakeTrustedCertificateStore(AndroidTrustedCertificateLookupResult.Present),
            nowEpochMillis = { 2_000L },
        )

        val result = verifier.verify(
            registration(root),
            "credential",
            CompanionCertificateArtifact(root.encoded, "knet-root-ca.crt"),
        )

        assertIs<CompanionCertificateState.Rejected>(result)
    }

    @Test
    fun missingInstalledRootCannotBeBypassedByAReusableTlsSession() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val client = FakeTlsClient(
            platformTrusted = AndroidCertificateTlsResult.Success(204, emptyMap(), ByteArray(0)),
        )
        val verifier = AndroidCompanionCertificateTrustVerifier(
            client = client,
            trustedCertificates = FakeTrustedCertificateStore(AndroidTrustedCertificateLookupResult.Absent),
            nowEpochMillis = { 2_000L },
        )

        val result = verifier.verify(
            registration(root),
            "credential",
            CompanionCertificateArtifact(root.encoded, "knet-root-ca.crt"),
        )

        assertIs<CompanionCertificateState.InstallationRequired>(result)
        assertEquals(0, client.platformTrustedCalls)
    }

    @Test
    fun unavailableTrustedCredentialStoreFailsClosedWithoutStartingTlsChallenge() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val client = FakeTlsClient(platformTrusted = AndroidCertificateTlsResult.TrustRejected)
        val verifier = AndroidCompanionCertificateTrustVerifier(
            client = client,
            trustedCertificates = FakeTrustedCertificateStore(AndroidTrustedCertificateLookupResult.Unavailable),
            nowEpochMillis = { 2_000L },
        )

        val result = assertIs<CompanionCertificateState.VerificationDeferred>(
            verifier.verify(
                registration(root),
                "credential",
                CompanionCertificateArtifact(root.encoded, "knet-root-ca.crt"),
            ),
        )

        assertEquals(CompanionFailureCode.CERTIFICATE_UNAVAILABLE, result.reason.code)
        assertEquals(0, client.platformTrustedCalls)
    }

    @Test
    fun unavailableDesktopDuringTrustChallengeDefersVerificationWithoutRejectingCertificate() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val client = FakeTlsClient(platformTrusted = AndroidCertificateTlsResult.Unavailable)
        val verifier = AndroidCompanionCertificateTrustVerifier(
            client = client,
            trustedCertificates = FakeTrustedCertificateStore(AndroidTrustedCertificateLookupResult.Present),
            nowEpochMillis = { 2_000L },
        )

        val result = assertIs<CompanionCertificateState.VerificationDeferred>(
            verifier.verify(
                registration(root),
                "credential",
                CompanionCertificateArtifact(root.encoded, "knet-root-ca.crt"),
            ),
        )

        assertEquals(CompanionFailureCode.TRANSPORT_UNAVAILABLE, result.reason.code)
        assertEquals(1, client.platformTrustedCalls)
    }

    @Test
    fun platformStoreReturnsPresenceOnlyForTheExactTrustedCertificate() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val store = PlatformAndroidTrustedCertificateStore(
            trustedCertificatesLoader = { sequenceOf(root) },
            ioContext = coroutineContext,
        )

        assertIs<AndroidTrustedCertificateLookupResult.Present>(
            store.lookup(root),
        )
        assertIs<AndroidTrustedCertificateLookupResult.Absent>(
            store.lookup(testCertificate("local-proxy-test-leaf.pem")),
        )
    }

    @Test
    fun platformStoreStopsEnumeratingAfterTheExactCertificateIsFound() = runTest {
        val root = testCertificate("local-proxy-test-ca.pem")
        val store = PlatformAndroidTrustedCertificateStore(
            trustedCertificatesLoader = {
                sequence {
                    yield(root)
                    error("certificate enumeration should have stopped")
                }
            },
            ioContext = coroutineContext,
        )

        assertIs<AndroidTrustedCertificateLookupResult.Present>(store.lookup(root))
    }

    @Test
    fun platformStoreConvertsCredentialProviderFailureToUnavailable() = runTest {
        val store = PlatformAndroidTrustedCertificateStore(
            trustedCertificatesLoader = { error("credential provider unavailable") },
            ioContext = coroutineContext,
        )

        assertIs<AndroidTrustedCertificateLookupResult.Unavailable>(
            store.lookup(testCertificate("local-proxy-test-ca.pem")),
        )
    }

    private class FakeTlsClient(
        private val pinned: AndroidCertificateTlsResult = AndroidCertificateTlsResult.Unavailable,
        private val platformTrusted: AndroidCertificateTlsResult = AndroidCertificateTlsResult.Unavailable,
    ) : AndroidCertificateTlsClient {
        var pinnedCalls: Int = 0
        var platformTrustedCalls: Int = 0
        var lastPinnedPath: String? = null

        override suspend fun executePinned(
            registration: CompanionRegistration,
            credential: String,
            path: String,
            challenge: CompanionCertificateChallengeNonce?,
            maximumBodyBytes: Int,
        ): AndroidCertificateTlsResult {
            pinnedCalls += 1
            lastPinnedPath = path
            return pinned
        }

        override suspend fun executePlatformTrusted(
            registration: CompanionRegistration,
            credential: String,
            path: String,
            challenge: CompanionCertificateChallengeNonce,
            expectedRoot: X509Certificate,
            maximumBodyBytes: Int,
        ): AndroidCertificateTlsResult {
            platformTrustedCalls += 1
            return platformTrusted
        }
    }

    private class FakeTrustedCertificateStore(
        private val result: AndroidTrustedCertificateLookupResult,
    ) : AndroidTrustedCertificateStore {
        override suspend fun lookup(
            rootCertificate: X509Certificate,
        ): AndroidTrustedCertificateLookupResult = result
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
