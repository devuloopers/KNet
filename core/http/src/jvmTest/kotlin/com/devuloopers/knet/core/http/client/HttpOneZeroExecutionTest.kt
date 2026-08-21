package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/** Wire-level qualification for the API Studio exact HTTP/1.0 transport. */
class HttpOneZeroExecutionTest {

    @Test
    fun `exact HTTP 1_0 emits an origin-form request and reports the observed response version`() = runBlocking {
        ServerSocket(0).use { server ->
            val observed = CompletableFuture<ObservedRequest>()
            val worker = serveOne(server, observed, closeDelimitedBody = true)
            val client = KNetApiClient()

            val result = client.executeDetailed(
                url = "http://127.0.0.1:${server.localPort}/legacy?mode=exact",
                method = HttpMethod.POST,
                headers = mapOf("X-Test" to "http-1.0"),
                body = OutboundRequestBody.Text("payload"),
                httpVersionPreference = HttpVersionPreference.HTTP_1_0,
            )

            val request = observed.get(5, TimeUnit.SECONDS)
            assertEquals("POST /legacy?mode=exact HTTP/1.0", request.requestLine)
            assertEquals("7", request.headers["content-length"])
            assertEquals("close", request.headers["connection"])
            assertEquals("payload", request.body)
            assertEquals("legacy-response", result.responseBody)
            assertEquals(
                ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_0),
                result.protocol,
            )
            client.close()
            worker.join(5_000)
        }
    }

    @Test
    fun `exact HTTP 1_0 uses absolute-form when routed through the active proxy`() = runBlocking {
        ServerSocket(0).use { proxy ->
            val observed = CompletableFuture<ObservedRequest>()
            val worker = serveOne(proxy, observed, closeDelimitedBody = false)
            val client = KNetApiClient()

            val result = client.executeDetailed(
                url = "http://origin.example.test/proxied?q=1",
                method = HttpMethod.GET,
                proxyPort = proxy.localPort,
                httpVersionPreference = HttpVersionPreference.HTTP_1_0,
            )

            val request = observed.get(5, TimeUnit.SECONDS)
            assertEquals("GET http://origin.example.test/proxied?q=1 HTTP/1.0", request.requestLine)
            assertEquals(200, result.statusCode)
            assertEquals("legacy-response", result.responseBody)
            client.close()
            worker.join(5_000)
        }
    }

    private fun serveOne(
        server: ServerSocket,
        observed: CompletableFuture<ObservedRequest>,
        closeDelimitedBody: Boolean,
    ): Thread = thread(name = "http-1.0-api-studio-test") {
        runCatching {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine()
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        headers[line.substring(0, separator).trim().lowercase()] =
                            line.substring(separator + 1).trim()
                    }
                }
                val bodyLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = CharArray(bodyLength).also { chars ->
                    var offset = 0
                    while (offset < chars.size) {
                        val read = reader.read(chars, offset, chars.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                }.concatToString()
                observed.complete(ObservedRequest(requestLine, headers, body))

                val responseBody = "legacy-response"
                val responseHead = buildString {
                    append("HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\n")
                    if (!closeDelimitedBody) append("Content-Length: ${responseBody.length}\r\n")
                    append("Connection: close\r\n\r\n")
                }
                socket.getOutputStream().apply {
                    write((responseHead + responseBody).toByteArray(Charsets.ISO_8859_1))
                    flush()
                }
            }
        }.onFailure(observed::completeExceptionally)
    }

    private data class ObservedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: String,
    )
}
