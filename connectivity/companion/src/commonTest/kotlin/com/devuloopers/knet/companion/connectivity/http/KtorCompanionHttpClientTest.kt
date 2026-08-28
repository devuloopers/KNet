package com.devuloopers.knet.companion.connectivity.http

import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class KtorCompanionHttpClientTest {
    @Test
    fun requestUsesEndpointPathHeadersAndBody() = runTest {
        val payload = "request-body".encodeToByteArray()
        var released = 0
        val provider = MockCompanionKtorClientProvider { companionRequest ->
            MockEngine { request ->
                assertEquals("192.0.2.10", request.url.host)
                assertEquals(8_183, request.url.port)
                assertEquals("/companion/test", request.url.encodedPath)
                assertEquals("companion.knet.local", request.headers[HttpHeaders.Host])
                assertEquals("Bearer device:credential", request.headers[HttpHeaders.Authorization])
                assertEquals("application/vnd.knet.request", request.body.contentType?.toString())
                assertContentEquals(payload, request.body.toByteArray())
                val responseBody = "response-body".encodeToByteArray()
                respond(
                    content = responseBody,
                    status = HttpStatusCode.Accepted,
                    headers = responseHeaders("application/vnd.knet.response", responseBody.size),
                )
            }.also {
                assertEquals(CompanionHttpSecurity.PinnedRoot::class, companionRequest.security::class)
            }
        }.withRelease { released += 1 }
        val client = KtorCompanionHttpClient(provider)

        val response = client.execute(
            CompanionHttpRequest(
                endpoint = CompanionServiceEndpoint("192.0.2.10", 8_183, CompanionEndpointScheme.HTTPS),
                method = CompanionHttpMethod.POST,
                path = "/companion/test",
                requestMediaType = "application/vnd.knet.request",
                acceptedMediaType = "application/vnd.knet.response",
                authorization = "Bearer device:credential",
                body = payload,
                maximumResponseBytes = 128,
                security = pinnedSecurity(),
            ),
        )

        assertEquals(202, response.statusCode)
        assertEquals("application/vnd.knet.response", response.mediaType)
        assertContentEquals("response-body".encodeToByteArray(), response.copyBody())
        assertEquals(1, released)
    }

    @Test
    fun responseLargerThanConfiguredBoundIsRejectedBeforeBodyConsumption() = runTest {
        val provider = MockCompanionKtorClientProvider {
            MockEngine {
                respond(
                    content = "oversized",
                    headers = responseHeaders("text/plain", declaredLength = 9),
                )
            }
        }
        val client = KtorCompanionHttpClient(provider)

        assertFailsWith<IllegalArgumentException> {
            client.execute(
                CompanionHttpRequest(
                    endpoint = CompanionServiceEndpoint("192.0.2.10", 8_183, CompanionEndpointScheme.HTTPS),
                    method = CompanionHttpMethod.GET,
                    path = "/companion/test",
                    maximumResponseBytes = 8,
                    security = pinnedSecurity(),
                ),
            )
        }
    }

    @Test
    fun additionalHeadersCannotOverrideTransportOwnedSecurityHeaders() {
        assertFailsWith<IllegalArgumentException> {
            CompanionHttpRequest(
                endpoint = CompanionServiceEndpoint("192.0.2.10", 8_183, CompanionEndpointScheme.HTTPS),
                method = CompanionHttpMethod.GET,
                path = "/companion/test",
                additionalHeaders = mapOf("Authorization" to "untrusted"),
                maximumResponseBytes = 8,
                security = pinnedSecurity(),
            )
        }
    }

    @Test
    fun bootstrapCleartextCapabilityAllowsOnlyThePublicRootGet() {
        val request = CompanionHttpRequest(
            endpoint = CompanionServiceEndpoint("192.0.2.10", 8_181, CompanionEndpointScheme.HTTP),
            method = CompanionHttpMethod.GET,
            path = CompanionBootstrapProtocol.ROOT_CERTIFICATE_PATH,
            acceptedMediaType = CompanionBootstrapProtocol.ROOT_CERTIFICATE_MEDIA_TYPE,
            maximumResponseBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
            security = CompanionHttpSecurity.BootstrapRootOnly,
        )

        assertEquals(CompanionHttpSecurity.BootstrapRootOnly, request.security)
    }

    @Test
    fun bootstrapCleartextCapabilityRejectsArbitraryAndSecretBearingRequests() {
        assertFailsWith<IllegalArgumentException> {
            CompanionHttpRequest(
                endpoint = CompanionServiceEndpoint("192.0.2.10", 8_181, CompanionEndpointScheme.HTTP),
                method = CompanionHttpMethod.GET,
                path = "/arbitrary",
                acceptedMediaType = CompanionBootstrapProtocol.ROOT_CERTIFICATE_MEDIA_TYPE,
                maximumResponseBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
                security = CompanionHttpSecurity.BootstrapRootOnly,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CompanionHttpRequest(
                endpoint = CompanionServiceEndpoint("192.0.2.10", 8_181, CompanionEndpointScheme.HTTP),
                method = CompanionHttpMethod.POST,
                path = CompanionBootstrapProtocol.ROOT_CERTIFICATE_PATH,
                acceptedMediaType = CompanionBootstrapProtocol.ROOT_CERTIFICATE_MEDIA_TYPE,
                authorization = "Bearer must-not-leave",
                body = byteArrayOf(1),
                maximumResponseBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
                security = CompanionHttpSecurity.BootstrapRootOnly,
            )
        }
    }

    private fun pinnedSecurity(): CompanionHttpSecurity.PinnedRoot = CompanionHttpSecurity.PinnedRoot(
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
        rootCertificateSha256 = Sha256Fingerprint("a".repeat(64)),
        transportIdentitySha256 = Sha256Fingerprint("b".repeat(64)),
    )
}

internal class MockCompanionKtorClientProvider(
    private val engineFactory: (CompanionHttpRequest) -> MockEngine,
) : CompanionKtorClientProvider {
    private var release: () -> Unit = {}

    fun withRelease(block: () -> Unit): MockCompanionKtorClientProvider = apply { release = block }

    override fun create(request: CompanionHttpRequest): CompanionKtorClientHandle = CompanionKtorClientHandle(
        client = HttpClient(engineFactory(request)) { configureCompanionClient() },
        releasePlatformResources = release,
    )
}

internal fun responseHeaders(mediaType: String, declaredLength: Int) = headersOf(
    HttpHeaders.ContentType to listOf(mediaType),
    HttpHeaders.ContentLength to listOf(declaredLength.toString()),
)
