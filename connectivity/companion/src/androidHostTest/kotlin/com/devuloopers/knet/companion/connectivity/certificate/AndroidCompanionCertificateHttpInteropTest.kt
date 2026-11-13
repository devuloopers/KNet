package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.connectivity.http.*
import com.devuloopers.knet.companion.connectivity.transport.testCertificateIdentity
import com.devuloopers.knet.companion.model.*
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/** Exercises native OkHttp parsing of real TLS certificate-challenge responses, without a mock HTTP engine. */
class AndroidCompanionCertificateHttpInteropTest {
    @Test
    fun nativeCertificateChallengeAcceptsBodyless204WithAbsentOrLegacyZeroLength(): Unit = runBlocking {
        for (length in listOf<String?>(null, "0")) {
            withServer("204 No Content", length, ByteArray(0)) { request ->
                val response = KtorCompanionHttpClient(AndroidCompanionKtorClientProvider()).execute(request)
                assertEquals(204, response.statusCode)
                assertContentEquals(ByteArray(0), response.copyBody())
                assertEquals(NONCE, response.headers[CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase()])
            }
        }
    }

    @Test
    fun nativeBodyBearingCertificateResponseStillRequiresLength(): Unit = runBlocking {
        withServer("200 OK", null, byteArrayOf(1)) { request ->
            assertFailsWith<IllegalArgumentException> {
                KtorCompanionHttpClient(AndroidCompanionKtorClientProvider()).execute(request)
            }
        }
    }

    private suspend fun withServer(status: String, length: String?, body: ByteArray,
        checkResponse: suspend (CompanionHttpRequest) -> Unit) {
        val identity = testCertificateIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME)
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("server", identity.leafPrivateKey, CharArray(0),
                arrayOf(identity.leafCertificate, identity.rootCertificate))
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
        val context = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
        val executor = Executors.newSingleThreadExecutor()
        val server = context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        server.soTimeout = 5000
        try {
            val responseSent = executor.submit {
                (server.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.inputStream.bufferedReader()
                    assertEquals("POST ${CompanionCertificateProtocol.TRUST_CHALLENGE_PATH} HTTP/1.1", reader.readLine())
                    val headers = buildMap {
                        while (true) {
                            val line = reader.readLine() ?: error("Truncated test request")
                            if (line.isEmpty()) break
                            put(line.substringBefore(':').lowercase(), line.substringAfter(':').trim())
                        }
                    }
                    assertEquals(NONCE, headers[CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase()])
                    assertEquals("0", headers["content-length"])
                    val response = buildString {
                        append("HTTP/1.1 $status\r\n")
                        append("${CompanionCertificateProtocol.CHALLENGE_HEADER}: $NONCE\r\n")
                        length?.let { append("Content-Length: $it\r\n") }
                        append("Connection: close\r\n\r\n")
                    }
                    socket.outputStream.write(response.encodeToByteArray())
                    socket.outputStream.write(body)
                    socket.outputStream.flush()
                }
            }
            val fingerprint = Sha256Fingerprint(identity.rootCertificate.sha256Hex())
            val request = CompanionHttpRequest(
                endpoint = CompanionServiceEndpoint("127.0.0.1", server.localPort, CompanionEndpointScheme.HTTPS),
                method = CompanionHttpMethod.POST,
                path = CompanionCertificateProtocol.TRUST_CHALLENGE_PATH,
                additionalHeaders = mapOf(CompanionCertificateProtocol.CHALLENGE_HEADER to NONCE),
                maximumResponseBytes = 1024,
                security = CompanionHttpSecurity.PinnedRoot(CompanionRootCertificate(identity.rootCertificate.encoded), fingerprint, fingerprint),
            )
            withTimeout(5000) { checkResponse(request) }
            responseSent.get(5, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private companion object {
        const val NONCE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
