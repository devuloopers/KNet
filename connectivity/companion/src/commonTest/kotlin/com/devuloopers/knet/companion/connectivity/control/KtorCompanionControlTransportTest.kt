package com.devuloopers.knet.companion.connectivity.control

import com.devuloopers.knet.companion.application.contract.CompanionControlAuthorization
import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.http.MockCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.responseHeaders
import com.devuloopers.knet.companion.model.CompanionControlProtocol
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class KtorCompanionControlTransportTest {
    @Test
    fun credentialRefreshUsesTypedPathMediaAndAuthorization() = runTest {
        val requestBody = "refresh-request".encodeToByteArray()
        val responseBody = "refresh-response".encodeToByteArray()
        val provider = MockCompanionKtorClientProvider { companionRequest ->
            assertIs<CompanionHttpSecurity.PinnedRoot>(companionRequest.security)
            MockEngine { request ->
                assertEquals(CompanionControlProtocol.REFRESH_PATH, request.url.encodedPath)
                assertEquals(
                    "Bearer device-1:${"c".repeat(32)}",
                    request.headers[HttpHeaders.Authorization],
                )
                assertEquals(
                    CompanionControlProtocol.REFRESH_REQUEST_MEDIA_TYPE,
                    request.body.contentType?.toString(),
                )
                assertContentEquals(requestBody, request.body.toByteArray())
                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = responseHeaders(
                        CompanionControlProtocol.REFRESH_RESPONSE_MEDIA_TYPE,
                        responseBody.size,
                    ),
                )
            }
        }
        val transport = KtorCompanionControlTransport(KtorCompanionHttpClient(provider))

        val response = transport.execute(
            CompanionControlRequest(
                endpoint = CompanionServiceEndpoint("192.0.2.1", 8_183, true),
                transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
                rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
                rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
                operation = CompanionControlOperation.REFRESH_CREDENTIAL,
                body = requestBody,
                authorization = CompanionControlAuthorization(
                    deviceId = RegisteredDeviceId("device-1"),
                    credential = "c".repeat(32),
                ),
            ),
        )

        assertEquals(200, response.statusCode)
        assertContentEquals(responseBody, response.copyBody())
    }
}
