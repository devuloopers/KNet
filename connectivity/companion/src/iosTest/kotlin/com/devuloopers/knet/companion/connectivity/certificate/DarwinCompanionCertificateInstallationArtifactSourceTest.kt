package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.http.MockCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.responseHeaders
import com.devuloopers.knet.companion.connectivity.testing.companionRegistrationFixture
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class DarwinCompanionCertificateInstallationArtifactSourceTest {
    @Test
    fun downloadsAuthenticatedAppleProfileForThePairedRoot() = runTest {
        val registration = companionRegistrationFixture()
        val profile = appleProfile(registration.rootCertificate.copyBytes()).encodeToByteArray()
        val provider = MockCompanionKtorClientProvider { companionRequest ->
            assertEquals(CompanionCertificateProtocol.APPLE_PROFILE_PATH, companionRequest.path)
            assertEquals(
                CompanionCertificateProtocol.APPLE_PROFILE_MEDIA_TYPE,
                companionRequest.acceptedMediaType,
            )
            MockEngine { request ->
                assertEquals(CompanionCertificateProtocol.APPLE_PROFILE_PATH, request.url.encodedPath)
                assertEquals(
                    CompanionCertificateProtocol.APPLE_PROFILE_MEDIA_TYPE,
                    request.headers[HttpHeaders.Accept],
                )
                assertEquals("Bearer device-1:credential", request.headers[HttpHeaders.Authorization])
                respond(
                    content = profile,
                    status = HttpStatusCode.OK,
                    headers = responseHeaders(CompanionCertificateProtocol.APPLE_PROFILE_MEDIA_TYPE, profile.size),
                )
            }
        }

        val result = assertIs<CompanionCertificateDownloadResult.Downloaded>(
            DarwinCompanionCertificateInstallationArtifactSource(KtorCompanionHttpClient(provider))
                .download(registration, "credential"),
        )

        assertEquals("knet-ca.mobileconfig", result.artifact.suggestedFileName)
        assertContentEquals(profile, result.artifact.copyBytes())
    }

    private fun appleProfile(root: ByteArray): String =
        "<plist><dict><string>com.apple.security.root</string><data>${Base64.encode(root)}</data></dict></plist>"
}
