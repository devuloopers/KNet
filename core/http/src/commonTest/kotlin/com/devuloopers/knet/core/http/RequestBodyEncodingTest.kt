package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.traffic.model.http.HttpMethod
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestBodyEncodingTest {

    @Test
    fun testJsonBodyEncoding() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("application/json", request.body.contentType?.toString()?.substringBefore(";"))
            respond(content = "OK", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(
            url = "https://api.knet.dev/json",
            method = HttpMethod.POST,
            body = OutboundRequestBody.Json("{\"key\":\"val\"}")
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun testXmlBodyEncoding() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("application/xml", request.body.contentType?.toString()?.substringBefore(";"))
            respond(content = "OK", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(
            url = "https://api.knet.dev/xml",
            method = HttpMethod.POST,
            body = OutboundRequestBody.Xml("<root><key>val</key></root>")
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun testRawTextBodyEncoding() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("text/plain", request.body.contentType?.toString()?.substringBefore(";"))
            respond(content = "OK", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(
            url = "https://api.knet.dev/raw",
            method = HttpMethod.POST,
            body = OutboundRequestBody.Text("plain text message")
        )

        assertTrue(result.isSuccess)
    }
}
