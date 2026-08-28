package com.devuloopers.knet.companion.connectivity.bootstrap

import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.http.MockCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.responseHeaders
import com.devuloopers.knet.companion.connectivity.http.sha256Hex
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class KtorCompanionBootstrapClientTest {
    @Test
    fun secretIsSentOnlyAfterPublicRootMatchesQrFingerprint() = runTest {
        val root = "public-root".encodeToByteArray()
        val invitation = "complete-invitation".encodeToByteArray()
        val observedPolicies = mutableListOf<CompanionHttpSecurity>()
        val provider = MockCompanionKtorClientProvider { companionRequest ->
            observedPolicies += companionRequest.security
            when (companionRequest.security) {
                CompanionHttpSecurity.BootstrapRootOnly -> rootResponse(root)
                is CompanionHttpSecurity.PinnedRoot -> redemptionResponse(companionRequest, invitation)
                is CompanionHttpSecurity.PlatformTrusted -> error("Bootstrap must not use platform trust.")
            }
        }
        val client = KtorCompanionBootstrapClient(KtorCompanionHttpClient(provider))
        val redemptionBody = "id=bootstrap-1\nsecret=${"r".repeat(32)}".encodeToByteArray()

        val result = assertIs<CompanionBootstrapResult.Response>(
            client.redeem(bootstrap(root.sha256Hex()), redemptionBody),
        )

        assertEquals(200, result.statusCode)
        assertContentEquals(invitation, result.copyBody())
        assertEquals(2, observedPolicies.size)
        assertIs<CompanionHttpSecurity.BootstrapRootOnly>(observedPolicies[0])
        assertIs<CompanionHttpSecurity.PinnedRoot>(observedPolicies[1])
    }

    @Test
    fun mismatchedPublicRootStopsBeforePinnedRequest() = runTest {
        var requests = 0
        val provider = MockCompanionKtorClientProvider {
            requests += 1
            rootResponse("different-root".encodeToByteArray())
        }
        val client = KtorCompanionBootstrapClient(KtorCompanionHttpClient(provider))

        val result = client.redeem(bootstrap("expected-root".encodeToByteArray().sha256Hex()), byteArrayOf(1))

        assertIs<CompanionBootstrapResult.IdentityRejected>(result)
        assertEquals(1, requests)
    }

    private fun rootResponse(root: ByteArray): MockEngine = MockEngine { request ->
        assertEquals(CompanionBootstrapProtocol.ROOT_CERTIFICATE_PATH, request.url.encodedPath)
        assertTrue(request.body.toByteArray().isEmpty())
        respond(
            content = root,
            status = HttpStatusCode.OK,
            headers = responseHeaders(CompanionBootstrapProtocol.ROOT_CERTIFICATE_MEDIA_TYPE, root.size),
        )
    }

    private fun redemptionResponse(
        companionRequest: CompanionHttpRequest,
        invitation: ByteArray,
    ): MockEngine = MockEngine { request ->
        assertEquals(CompanionBootstrapProtocol.REDEEM_PATH, request.url.encodedPath)
        assertContentEquals(companionRequest.copyBody(), request.body.toByteArray())
        assertTrue(request.body.toByteArray().decodeToString().contains("secret=${"r".repeat(32)}"))
        respond(
            content = invitation,
            status = HttpStatusCode.OK,
            headers = responseHeaders(CompanionBootstrapProtocol.RESPONSE_MEDIA_TYPE, invitation.size),
        )
    }

    private fun bootstrap(rootSha256: String): CompanionPairingBootstrap = CompanionPairingBootstrap(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        id = CompanionBootstrapId("bootstrap-1"),
        retrievalSecret = CompanionBootstrapSecret("r".repeat(32)),
        expiresAtEpochMillis = 2_000L,
        rootCertificateEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_181, CompanionEndpointScheme.HTTP),
        retrievalEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_183, CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint(rootSha256),
    )
}
