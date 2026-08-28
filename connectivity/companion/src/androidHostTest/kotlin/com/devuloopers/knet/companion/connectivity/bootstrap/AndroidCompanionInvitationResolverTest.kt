package com.devuloopers.knet.companion.connectivity.bootstrap

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolutionResult
import com.devuloopers.knet.companion.connectivity.certificate.validatedBootstrapRoot
import com.devuloopers.knet.companion.connectivity.certificate.sha256Hex
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInvitationResponseCodec
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class AndroidCompanionInvitationResolverTest {
    @Test
    fun validPinnedResponseResolvesCompleteInvitation() = runTest {
        val invitation = invitation()
        var sentBody = ByteArray(0)
        val resolver = DefaultCompanionInvitationResolver(
            bootstrapClient = CompanionBootstrapClient { _, body ->
                sentBody = body
                CompanionBootstrapResult.Response(
                    200,
                    CompanionBootstrapProtocol.RESPONSE_MEDIA_TYPE,
                    CompanionInvitationResponseCodec().encode(invitation),
                )
            },
        )

        val result = assertIs<CompanionInvitationResolutionResult.Resolved>(resolver.resolve(bootstrap()))

        assertEquals(invitation, result.invitation)
        assertTrue(sentBody.decodeToString().contains("secret=${"r".repeat(32)}"))
    }

    @Test
    fun identityMismatchFailsClosedWithoutDecodingResponse() = runTest {
        val resolver = DefaultCompanionInvitationResolver(
            bootstrapClient = CompanionBootstrapClient { _, _ -> CompanionBootstrapResult.IdentityRejected },
        )

        val result = assertIs<CompanionInvitationResolutionResult.Rejected>(resolver.resolve(bootstrap()))

        assertEquals(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH, result.failure.code)
    }

    @Test
    fun replayOrExpiredResponseIsPresentationSafe() = runTest {
        val resolver = DefaultCompanionInvitationResolver(
            bootstrapClient = CompanionBootstrapClient { _, _ ->
                CompanionBootstrapResult.Response(401, "text/plain", "invitation_rejected".encodeToByteArray())
            },
        )

        val result = assertIs<CompanionInvitationResolutionResult.Rejected>(resolver.resolve(bootstrap()))

        assertEquals(CompanionFailureCode.INVITATION_RETRIEVAL_FAILED, result.failure.code)
        assertTrue(!result.failure.message.contains("secret", ignoreCase = true))
    }

    @Test
    fun downloadedBootstrapRootRequiresAValidCaMatchingTheQrPin() {
        val root = testCertificate("local-proxy-test-ca.pem")
        val leaf = testCertificate("local-proxy-test-leaf.pem")

        assertEquals(root, root.encoded.validatedBootstrapRoot(root.sha256Hex()))
        assertEquals(null, root.encoded.validatedBootstrapRoot("0".repeat(64)))
        assertEquals(null, leaf.encoded.validatedBootstrapRoot(leaf.sha256Hex()))
    }

    private fun bootstrap(): CompanionPairingBootstrap = CompanionPairingBootstrap(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        id = CompanionBootstrapId("bootstrap-1"),
        retrievalSecret = CompanionBootstrapSecret("r".repeat(32)),
        expiresAtEpochMillis = 2_000L,
        rootCertificateEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_181, CompanionEndpointScheme.HTTP),
        retrievalEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_183, CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    private fun invitation(): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("KNet Desktop"),
        pairing = PairingInvitation(
            PairingInvitationId("pairing-1"),
            "p".repeat(32),
            2_000L,
            setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_183, CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_182, CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
    )

    private fun testCertificate(name: String): X509Certificate {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("tls/$name"))
        return stream.use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }
    }
}
