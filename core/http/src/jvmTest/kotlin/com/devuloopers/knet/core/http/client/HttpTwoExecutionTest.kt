package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.testingserver.http2.Http2TlsLabProperties
import com.devuloopers.knet.testingserver.http2.Http2TlsLabServer
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exact-version qualification for API Studio's JVM HTTP/2 adapter. */
class HttpTwoExecutionTest {

    @Test
    fun `exact http two negotiates alpn and reports observed protocol`() = runTest {
        val server = Http2TlsLabServer(
            Http2TlsLabProperties(host = LOOPBACK_HOST, port = 0),
        )
        server.start()
        try {
            val client = KNetApiClient(
                configuration = HttpClientConfiguration(
                    verifySsl = false,
                    retryCount = 0,
                ),
            )
            val result = client.executeDetailed(
                url = "https://localhost:${server.boundPort}/lab/v1/http2/echo",
                method = HttpMethod.POST,
                body = OutboundRequestBody.Text("through-api-studio"),
                httpVersionPreference = HttpVersionPreference.HTTP_2,
            )

            assertTrue(result.isSuccess, result.errorMessage)
            assertEquals("HTTP/2", result.protocol?.token)
            assertTrue(result.responseBody.contains("through-api-studio"))
            val reused = client.executeDetailed(
                url = "https://localhost:${server.boundPort}/lab/v1/http2/echo",
                method = HttpMethod.POST,
                body = OutboundRequestBody.Text("reused-client"),
                httpVersionPreference = HttpVersionPreference.HTTP_2,
            )
            assertTrue(reused.isSuccess, reused.errorMessage)
            assertEquals(1, server.acceptedConnectionCount)
            client.close()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `exact http two fails closed when origin supports only http one`() = runTest {
        val server = HttpServer.create(InetSocketAddress(LOOPBACK_HOST, 0), 0).apply {
            createContext("/") { exchange ->
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }
        try {
            val result = KNetApiClient().executeDetailed(
                url = "http://$LOOPBACK_HOST:${server.address.port}/",
                httpVersionPreference = HttpVersionPreference.HTTP_2,
            )

            assertFalse(result.isSuccess)
            assertEquals(0, result.statusCode)
            assertTrue(result.errorMessage.orEmpty().contains("Exact HTTP/2"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `auto negotiates http two and reports observed protocol`() = runTest {
        val server = Http2TlsLabServer(
            Http2TlsLabProperties(host = LOOPBACK_HOST, port = 0),
        )
        server.start()
        try {
            val client = KNetApiClient(
                configuration = HttpClientConfiguration(verifySsl = false, retryCount = 0),
            )
            val result = client.executeDetailed(
                url = "https://localhost:${server.boundPort}/lab/v1/http2/echo",
                httpVersionPreference = HttpVersionPreference.AUTO,
            )

            assertTrue(result.isSuccess, result.errorMessage)
            assertEquals("HTTP/2", result.protocol?.token)
            client.close()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `auto falls back to http one and reports observed protocol`() = runTest {
        val server = HttpServer.create(InetSocketAddress(LOOPBACK_HOST, 0), 0).apply {
            createContext("/") { exchange ->
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }
        try {
            val client = KNetApiClient(
                configuration = HttpClientConfiguration(retryCount = 0),
            )
            val result = client.executeDetailed(
                url = "http://$LOOPBACK_HOST:${server.address.port}/",
                httpVersionPreference = HttpVersionPreference.AUTO,
            )

            assertTrue(result.isSuccess, result.errorMessage)
            assertEquals("HTTP/1.1", result.protocol?.token)
            client.close()
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
    }
}
