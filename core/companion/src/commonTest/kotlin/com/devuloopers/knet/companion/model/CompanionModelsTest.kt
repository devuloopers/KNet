package com.devuloopers.knet.companion.model

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionModelsTest {
    @Test
    fun registrationNeverContainsCredentialValue() {
        val registration = registration()

        assertEquals("credential-reference", registration.credentialReference.value)
        assertEquals(setOf(DeviceScope.PROXY_STREAM), registration.scopes)
    }

    @Test
    fun fingerprintsRejectUppercaseAndWrongLength() {
        assertFailsWith<IllegalArgumentException> { Sha256Fingerprint("A".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { Sha256Fingerprint("a".repeat(63)) }
    }

    @Test
    fun certificateEnrollmentMatchesOnlyItsExactDesktopRoot() {
        val registration = registration()
        val enrollment = CompanionCertificateEnrollment(
            registration.desktopId,
            registration.rootCertificateSha256,
            completedAtEpochMillis = 1_500L,
        )

        assertTrue(enrollment.matches(registration))
        assertFalse(enrollment.matches(registration.copy(rootCertificateSha256 = Sha256Fingerprint("c".repeat(64)))))
        assertFailsWith<IllegalArgumentException> {
            enrollment.copy(completedAtEpochMillis = -1L)
        }
    }

    @Test
    fun invitationRequiresSecureControlAndProxyEndpoints() {
        assertFailsWith<IllegalArgumentException> {
            invitation().copy(controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTP))
        }
        assertFailsWith<IllegalArgumentException> {
            invitation().copy(proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, scheme = CompanionEndpointScheme.HTTP))
        }
    }

    @Test
    fun endpointRejectsUrlSyntaxWhitespaceAndControlCharacters() {
        listOf(
            "https://desktop.local",
            "desktop.local/path",
            "desktop.local?query",
            "desktop local",
            "desktop.local\n",
        ).forEach { unsafeHost ->
            assertFailsWith<IllegalArgumentException> {
                CompanionServiceEndpoint(unsafeHost, 8183, scheme = CompanionEndpointScheme.HTTPS)
            }
        }
    }

    @Test
    fun identifierLengthsAreBounded() {
        assertFailsWith<IllegalArgumentException> { CompanionDesktopId("d".repeat(129)) }
        assertFailsWith<IllegalArgumentException> { CompanionCredentialReference("r".repeat(513)) }
    }

    @Test
    fun companionDeviceDisplayNameIsTrimmedBoundedAndControlCharacterFree() {
        assertEquals("Pixel 9 · A7F2", CompanionDeviceDisplayName("Pixel 9 · A7F2").value)
        assertFailsWith<IllegalArgumentException> { CompanionDeviceDisplayName("") }
        assertFailsWith<IllegalArgumentException> { CompanionDeviceDisplayName(" Pixel") }
        assertFailsWith<IllegalArgumentException> { CompanionDeviceDisplayName("Pixel\n9") }
        assertFailsWith<IllegalArgumentException> {
            CompanionDeviceDisplayName("x".repeat(CompanionDeviceDisplayName.MAXIMUM_LENGTH + 1))
        }
    }

    @Test
    fun companionDesktopDisplayNameIsTrimmedBoundedAndControlCharacterFree() {
        assertEquals("Development Mac", CompanionDesktopDisplayName("Development Mac").value)
        assertFailsWith<IllegalArgumentException> { CompanionDesktopDisplayName("") }
        assertFailsWith<IllegalArgumentException> { CompanionDesktopDisplayName(" KNet Desktop") }
        assertFailsWith<IllegalArgumentException> { CompanionDesktopDisplayName("KNet\nDesktop") }
        assertFailsWith<IllegalArgumentException> {
            CompanionDesktopDisplayName("x".repeat(CompanionDesktopDisplayName.MAXIMUM_LENGTH + 1))
        }
    }

    @Test
    fun companionEndpointPreservesItsStronglyTypedScheme() {
        assertEquals(
            CompanionEndpointScheme.HTTP,
            CompanionServiceEndpoint("desktop.local", 8_181, CompanionEndpointScheme.HTTP).scheme,
        )
        assertEquals(
            CompanionEndpointScheme.HTTPS,
            CompanionServiceEndpoint("desktop.local", 8_183, CompanionEndpointScheme.HTTPS).scheme,
        )
    }

    @Test
    fun certificateChallengeNonceAcceptsOnlyBoundedBase64UrlText() {
        assertEquals("a".repeat(43), CompanionCertificateChallengeNonce("a".repeat(43)).value)
        assertFailsWith<IllegalArgumentException> { CompanionCertificateChallengeNonce("a".repeat(31)) }
        assertFailsWith<IllegalArgumentException> { CompanionCertificateChallengeNonce("a".repeat(129)) }
        assertFailsWith<IllegalArgumentException> { CompanionCertificateChallengeNonce("a".repeat(42) + "+") }
    }

    @Test
    fun companionRootCertificateIsBoundedAndDefensivelyCopied() {
        val input = byteArrayOf(1, 2, 3)
        val certificate = CompanionRootCertificate(input)
        input[0] = 9
        val copy = certificate.copyBytes()
        copy[1] = 9

        assertEquals(1, certificate.copyBytes()[0])
        assertEquals(2, certificate.copyBytes()[1])
        assertFailsWith<IllegalArgumentException> { CompanionRootCertificate(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> {
            CompanionRootCertificate(ByteArray(CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES + 1))
        }
        assertFalse(certificate == CompanionRootCertificate(byteArrayOf(9, 2, 3)))
    }

    @Test
    fun deferredCertificateVerificationAcceptsOnlyRecoverableFailures() {
        val recoverable = CompanionFailure(
            code = CompanionFailureCode.TRANSPORT_UNAVAILABLE,
            message = "The paired desktop is unavailable.",
            recoverable = true,
        )
        val terminal = CompanionFailure(
            code = CompanionFailureCode.CERTIFICATE_NOT_TRUSTED,
            message = "The certificate was rejected.",
            recoverable = false,
        )

        assertEquals(
            recoverable,
            CompanionCertificateState.VerificationDeferred(recoverable).reason,
        )
        assertFailsWith<IllegalArgumentException> {
            CompanionCertificateState.VerificationDeferred(terminal)
        }
    }

    @Test
    fun companionBootstrapPayloadIsSmallAndRoundTripsCanonicalVersionThreeFields() {
        val bootstrap = bootstrap()
        val codec = CompanionBootstrapPayloadCodec()

        val payload = codec.encode(bootstrap)

        assertTrue(payload.startsWith("knet://pair/v3?"))
        assertTrue(payload.length < 512)
        assertFalse(payload.contains("rootDer"))
        assertFalse(payload.contains("desktopName"))
        assertEquals(bootstrap, codec.decode(payload))
    }

    @Test
    fun companionBootstrapPayloadRejectsLegacyAndDuplicateFields() {
        val codec = CompanionBootstrapPayloadCodec()
        val payload = codec.encode(bootstrap())

        assertFailsWith<IllegalArgumentException> {
            codec.decode(payload.replace("knet://pair/v3?", "knet://pair/v2?"))
        }
        assertFailsWith<IllegalArgumentException> {
            codec.decode("$payload&id=duplicate")
        }
    }

    @Test
    fun companionBootstrapSeparatesOpenPublicRootDeliveryFromSecureRedemption() {
        assertFailsWith<IllegalArgumentException> {
            bootstrap().copy(
                rootCertificateEndpoint = CompanionServiceEndpoint("192.168.1.2", 8181, scheme = CompanionEndpointScheme.HTTPS),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            bootstrap().copy(
                retrievalEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTP),
            )
        }
    }

    @Test
    fun completeInvitationResponseAndRedemptionRequestRoundTripSeparately() {
        val invitation = invitation().copy(desktopDisplayName = CompanionDesktopDisplayName("Development Mac & Lab"))
        val invitationCodec = CompanionInvitationResponseCodec()
        val redemption = CompanionBootstrapRedemptionRequest(bootstrap().id, bootstrap().retrievalSecret)
        val redemptionCodec = CompanionBootstrapRedemptionCodec()

        assertEquals(invitation, invitationCodec.decode(invitationCodec.encode(invitation)))
        assertEquals(redemption, redemptionCodec.decode(redemptionCodec.encode(redemption)))
    }

    @Test
    fun pairingAndCredentialRefreshControlBodiesRoundTrip() {
        val completion = PairingCompletionRequest(
            invitationId = PairingInvitationId("invitation-1"),
            invitationSecret = "s".repeat(32),
            deviceId = RegisteredDeviceId("device-1"),
            displayName = "Development Pixel",
            publicKeyEncoded = "public-key-material",
            proofSignatureEncoded = "proof-signature-material",
            proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
        )
        val pairingGrant = CompanionPairingGrant(
            credential = "c".repeat(48),
            scopes = setOf(DeviceScope.PROXY_STREAM, DeviceScope.SETUP_ARTIFACT_READ),
            credentialExpiresAtEpochMillis = 9_000L,
        )
        val refreshRequest = CompanionCredentialRefreshRequest(RegisteredDeviceId("device-1"))
        val refreshGrant = CompanionCredentialRefreshGrant("n".repeat(48), 10_000L)

        assertEquals(
            completion,
            CompanionPairingCompletionCodec().run { decode(encode(completion)) },
        )
        assertEquals(
            pairingGrant,
            CompanionPairingGrantCodec().run { decode(encode(pairingGrant)) },
        )
        assertEquals(
            refreshRequest,
            CompanionCredentialRefreshRequestCodec().run { decode(encode(refreshRequest)) },
        )
        assertEquals(
            refreshGrant,
            CompanionCredentialRefreshGrantCodec().run { decode(encode(refreshGrant)) },
        )
    }

    @Test
    fun controlBodyCodecsRejectDuplicateUnexpectedAndOversizedFields() {
        val refreshCodec = CompanionCredentialRefreshRequestCodec()

        assertFailsWith<IllegalArgumentException> {
            refreshCodec.decode("deviceId=device-1&deviceId=device-2".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            refreshCodec.decode("deviceId=device-1&unexpected=value".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            refreshCodec.decode(ByteArray(CompanionControlProtocol.MAXIMUM_REQUEST_BYTES + 1))
        }
    }

    private fun bootstrap(): CompanionPairingBootstrap = CompanionPairingBootstrap(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        id = CompanionBootstrapId("bootstrap-1"),
        retrievalSecret = CompanionBootstrapSecret("r".repeat(32)),
        expiresAtEpochMillis = 2_000L,
        rootCertificateEndpoint = CompanionServiceEndpoint("192.168.1.2", 8181, scheme = CompanionEndpointScheme.HTTP),
        retrievalEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    private fun invitation(): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
        pairing = PairingInvitation(
            id = PairingInvitationId("invitation-1"),
            secret = "s".repeat(32),
            expiresAtEpochMillis = 2_000L,
            scopes = setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, scheme = CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
    )

    private fun registration(): CompanionRegistration = CompanionRegistration(
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
        deviceId = RegisteredDeviceId("device-1"),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, scheme = CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
        credentialReference = CompanionCredentialReference("credential-reference"),
        scopes = setOf(DeviceScope.PROXY_STREAM),
        pairedAtEpochMillis = 1_000L,
        credentialExpiresAtEpochMillis = 2_000L,
    )
}
