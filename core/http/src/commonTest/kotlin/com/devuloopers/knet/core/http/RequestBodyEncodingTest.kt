package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.model.RequestBodyType
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
        val result = client.execute(
            url = "https://api.knet.dev/json",
            method = "POST",
            body = "{\"key\":\"val\"}",
            bodyType = RequestBodyType.JSON
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
        val result = client.execute(
            url = "https://api.knet.dev/xml",
            method = "POST",
            body = "<root><key>val</key></root>",
            bodyType = RequestBodyType.XML
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
        val result = client.execute(
            url = "https://api.knet.dev/raw",
            method = "POST",
            body = "plain text message",
            bodyType = RequestBodyType.RAW_TEXT
        )

        assertTrue(result.isSuccess)
    }
}
