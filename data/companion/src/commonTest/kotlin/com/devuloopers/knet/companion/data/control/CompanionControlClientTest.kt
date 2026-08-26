package com.devuloopers.knet.companion.data.control

import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CompanionControlClientTest {
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
                    body = """{"credential":"grant-secret","scopes":["PROXY_STREAM"],"credential_expires_at_epoch_millis":9000}"""
                        .encodeToByteArray(),
                )
            },
        )

        val result = client.pair(invitation(), identity(), " Pixel ")

        val paired = assertIs<CompanionPairingClientResult.Paired>(result)
        assertEquals("grant-secret", paired.credential)
        assertEquals(setOf(DeviceScope.PROXY_STREAM), paired.scopes)
        assertTrue(requireNotNull(signedMessage).contains(DeviceProofAlgorithm.ECDSA_P256_SHA256.name))
        val request = requireNotNull(capturedRequest)
        assertEquals("/companion/v1/pair", request.path)
        assertEquals(invitation().controlEndpoint, request.endpoint)
        assertEquals(invitation().transportIdentitySha256, request.transportIdentitySha256)
        assertEquals(null, request.authorizationCredential)
        val payload = Json.parseToJsonElement(request.copyBody().decodeToString()).jsonObject
        assertEquals("one-time-secret!", payload.getValue("invitation_secret").jsonPrimitive.content)
        assertEquals(DeviceProofAlgorithm.ECDSA_P256_SHA256.name, payload.getValue("proof_algorithm").jsonPrimitive.content)
        assertEquals("signed-proof", payload.getValue("proof_signature_encoded").jsonPrimitive.content)
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
    fun requestAndResponseOwnDefensiveBodyCopies() {
        val requestBytes = byteArrayOf(1, 2, 3)
        val responseBytes = byteArrayOf(4, 5, 6)
        val request = CompanionControlRequest(
            endpoint = invitation().controlEndpoint,
            transportIdentitySha256 = invitation().transportIdentitySha256,
            path = "/companion/v1/pair",
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
        desktopDisplayName = "Development Mac",
        pairing = PairingInvitation(
            id = PairingInvitationId("invitation-1"),
            secret = "one-time-secret!",
            expiresAtEpochMillis = 5_000,
            scopes = setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, true),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    private fun identity(): CompanionDeviceIdentity = CompanionDeviceIdentity(
        deviceId = RegisteredDeviceId("device-1"),
        publicKeyEncoded = "public-key",
        privateKeyReference = "private-key-reference",
        proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
    )
}
