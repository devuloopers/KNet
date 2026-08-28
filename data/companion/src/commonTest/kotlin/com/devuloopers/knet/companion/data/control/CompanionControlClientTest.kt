package com.devuloopers.knet.companion.data.control

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.application.contract.CompanionControlResponse
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionCredentialRefreshResult
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationResult
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.model.CompanionPairingCompletionCodec
import com.devuloopers.knet.companion.model.CompanionPairingGrant
import com.devuloopers.knet.companion.model.CompanionPairingGrantCodec
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopRuntimeId
import com.devuloopers.knet.companion.model.CompanionDiscoveryProtocol
import com.devuloopers.knet.companion.model.CompanionEndpointDescriptor
import com.devuloopers.knet.companion.model.CompanionEndpointReconciliationCodec
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshGrant
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshGrantCodec
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshRequestCodec
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CompanionControlClientTest {
    @Test
    fun endpointReconciliationUsesCandidateAddressButRetainsExistingPinnedIdentity() = runTest {
        var capturedRequest: CompanionControlRequest? = null
        val descriptor = CompanionEndpointDescriptor(
            protocolVersion = CompanionDiscoveryProtocol.VERSION,
            desktopId = CompanionDesktopId("11111111-1111-4111-8111-111111111111"),
            acceptedLegacyIds = setOf(registration().desktopId),
            runtimeId = CompanionDesktopRuntimeId.parse("22222222-2222-4222-8222-222222222222"),
            controlPort = 8183,
            proxyPort = 8182,
        )
        val codec = CompanionEndpointReconciliationCodec()
        val client = DefaultCompanionEndpointReconciliationClient(
            CompanionControlTransport { request ->
                capturedRequest = request
                CompanionControlResponse(200, codec.encodeDescriptor(descriptor))
            },
            codec,
        )
        val candidateEndpoint = CompanionServiceEndpoint("192.168.1.77", 8183, scheme = CompanionEndpointScheme.HTTPS)

        val result = assertIs<CompanionEndpointReconciliationResult.Verified>(
            client.reconcile(registration(), candidateEndpoint, "current-credential-value"),
        )

        assertEquals(descriptor, result.descriptor)
        val request = requireNotNull(capturedRequest)
        assertEquals(candidateEndpoint, request.endpoint)
        assertEquals(registration().rootCertificateSha256, request.rootCertificateSha256)
        assertEquals(registration().transportIdentitySha256, request.transportIdentitySha256)
        assertEquals(CompanionControlOperation.RECONCILE_ENDPOINTS, request.operation)
        assertEquals("current-credential-value", request.authorization?.credential())
        assertEquals(registration().desktopId, codec.decodeRequest(request.copyBody()).desktopId)
    }

    @Test
    fun pairingSignsAlgorithmBoundTranscriptAndUsesPinnedEndpoint() = runTest {
        var capturedRequest: CompanionControlRequest? = null
        var signedMessage: String? = null
        val client = DefaultCompanionPairingClient(
            signer = CompanionDeviceProofSigner { _, message ->
                signedMessage = message
                "signed-proof"
            },
            transport = CompanionControlTransport { request ->
                capturedRequest = request
                CompanionControlResponse(
                    statusCode = 200,
                    body = CompanionPairingGrantCodec().encode(
                        CompanionPairingGrant("grant-secret-value", setOf(DeviceScope.PROXY_STREAM), 9_000L),
                    ),
                )
            },
        )

        val result = client.pair(invitation(), identity(), " Pixel ")

        val paired = assertIs<CompanionPairingClientResult.Paired>(result)
        assertEquals("grant-secret-value", paired.credential)
        assertEquals(setOf(DeviceScope.PROXY_STREAM), paired.scopes)
        assertTrue(requireNotNull(signedMessage).contains(DeviceProofAlgorithm.ECDSA_P256_SHA256.name))
        val request = requireNotNull(capturedRequest)
        assertEquals(CompanionControlOperation.PAIR, request.operation)
        assertEquals(invitation().controlEndpoint, request.endpoint)
        assertEquals(invitation().transportIdentitySha256, request.transportIdentitySha256)
        assertEquals(invitation().rootCertificateSha256, request.rootCertificateSha256)
        assertEquals(invitation().rootCertificate, request.rootCertificate)
        assertEquals(null, request.authorization)
        val payload = CompanionPairingCompletionCodec().decode(request.copyBody())
        assertEquals("one-time-secret!", payload.invitationSecret)
        assertEquals(DeviceProofAlgorithm.ECDSA_P256_SHA256, payload.proofAlgorithm)
        assertEquals("signed-proof", payload.proofSignatureEncoded)
    }

    @Test
    fun rejectedControlResponseDoesNotExposeResponseBody() = runTest {
        val client = DefaultCompanionPairingClient(
            signer = CompanionDeviceProofSigner { _, _ -> "signed-proof" },
            transport = CompanionControlTransport {
                CompanionControlResponse(401, "credential=must-not-leak".encodeToByteArray())
            },
        )

        val result = assertIs<CompanionPairingClientResult.Rejected>(
            client.pair(invitation(), identity(), "Pixel"),
        )

        assertEquals("Desktop rejected the companion request (HTTP 401).", result.failure.message)
        assertFalse(result.failure.message.contains("must-not-leak"))
    }

    @Test
    fun credentialRefreshUsesTypedAuthorizationAndBoundedGrant() = runTest {
        var capturedRequest: CompanionControlRequest? = null
        val client = DefaultCompanionPairingClient(
            signer = CompanionDeviceProofSigner { _, _ -> "signed-proof" },
            transport = CompanionControlTransport { request ->
                capturedRequest = request
                CompanionControlResponse(
                    200,
                    CompanionCredentialRefreshGrantCodec().encode(
                        CompanionCredentialRefreshGrant("new-credential-value", 12_000L),
                    ),
                )
            },
        )

        val result = assertIs<CompanionCredentialRefreshResult.Refreshed>(
            client.refresh(registration(), "current-credential-value"),
        )

        assertEquals("new-credential-value", result.credential)
        val request = requireNotNull(capturedRequest)
        assertEquals(CompanionControlOperation.REFRESH_CREDENTIAL, request.operation)
        assertEquals("device-1", request.authorization?.deviceId?.value)
        assertEquals("current-credential-value", request.authorization?.credential())
        assertEquals("device-1", CompanionCredentialRefreshRequestCodec().decode(request.copyBody()).deviceId.value)
    }

    @Test
    fun requestAndResponseOwnDefensiveBodyCopies() {
        val requestBytes = byteArrayOf(1, 2, 3)
        val responseBytes = byteArrayOf(4, 5, 6)
        val request = CompanionControlRequest(
            endpoint = invitation().controlEndpoint,
            transportIdentitySha256 = invitation().transportIdentitySha256,
            rootCertificateSha256 = invitation().rootCertificateSha256,
            rootCertificate = invitation().rootCertificate,
            operation = CompanionControlOperation.PAIR,
            body = requestBytes,
        )
        val response = CompanionControlResponse(200, responseBytes)

        requestBytes[0] = 9
        responseBytes[0] = 9
        val copiedRequest = request.copyBody().also { it[1] = 9 }
        val copiedResponse = response.copyBody().also { it[1] = 9 }

        assertContentEquals(byteArrayOf(1, 2, 3), request.copyBody())
        assertContentEquals(byteArrayOf(4, 5, 6), response.copyBody())
        assertContentEquals(byteArrayOf(1, 9, 3), copiedRequest)
        assertContentEquals(byteArrayOf(4, 9, 6), copiedResponse)
    }

    private fun invitation(): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
        pairing = PairingInvitation(
            id = PairingInvitationId("invitation-1"),
            secret = "one-time-secret!",
            expiresAtEpochMillis = 5_000,
            scopes = setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
    )

    private fun identity(): CompanionDeviceIdentity = CompanionDeviceIdentity(
        deviceId = RegisteredDeviceId("device-1"),
        publicKeyEncoded = "public-key",
        privateKeyReference = "private-key-reference",
        proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
    )

    private fun registration(): CompanionRegistration = CompanionRegistration(
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
        deviceId = RegisteredDeviceId("device-1"),
        controlEndpoint = invitation().controlEndpoint,
        proxyEndpoint = invitation().proxyEndpoint,
        transportIdentitySha256 = invitation().transportIdentitySha256,
        rootCertificateSha256 = invitation().rootCertificateSha256,
        rootCertificate = invitation().rootCertificate,
        credentialReference = CompanionCredentialReference("credential-reference"),
        scopes = setOf(DeviceScope.PROXY_STREAM),
        pairedAtEpochMillis = 1_000L,
        credentialExpiresAtEpochMillis = 9_000L,
    )
}
