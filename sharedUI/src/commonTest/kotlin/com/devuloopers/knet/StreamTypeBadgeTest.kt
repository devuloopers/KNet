package com.devuloopers.knet

import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.inspector.model.requestContentTypeBadge
import com.devuloopers.knet.domain.inspector.model.responseContentTypeBadge
import com.devuloopers.knet.domain.utils.prettyPrintBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests verifying smart Content-Type badge extraction
 * and body formatting across all streaming response types:
 *  - Firestore / gRPC Channel JSON Streams
 *  - Binary gRPC-Web Streams
 *  - Server-Sent Events (SSE) Streams
 *  - Multi-line NDJSON Streams
 *  - Form URL-Encoded POST bodies
 *  - Standard JSON, Images, Protobuf, and HTML
 */
class StreamTypeBadgeTest {

    private fun createTx(
        reqHeaders: Map<String, String> = emptyMap(),
        resHeaders: Map<String, String> = emptyMap(),
        reqBody: String = "",
        resBody: String = ""
    ): TransactionUiModel {
        return TransactionUiModel(
            id = 1,
            method = "GET",
            host = "firestore.googleapis.com",
            path = "/Listen/channel",
            status = 200,
            statusText = "OK",
            time = "1270 ms",
            size = "123 B",
            dateGroup = "Today",
            requestBody = reqBody,
            responseBody = resBody,
            queryParams = emptyMap(),
            requestHeaders = reqHeaders,
            responseHeaders = resHeaders,
            timings = com.devuloopers.knet.model.HttpTimings()
        )
    }

    // -------------------------------------------------------------------------
    // Stream Badge Extraction Tests
    // -------------------------------------------------------------------------

    @Test
    fun testFirestoreGrpcChannelJsonStreamBadge() {
        val firestoreStream = """
            16
            [[162,["noop"]]]99
            [[163,[{"filter":{"targetId":10,"unchangedNames":{"bits":{}}}}]]]
        """.trimIndent()

        val tx = createTx(
            resHeaders = mapOf("content-type" to "text/plain; charset=utf-8"),
            resBody = firestoreStream
        )

        assertEquals("JSON Stream", tx.responseContentTypeBadge)

        val cleanedBody = prettyPrintBody(firestoreStream)
        // Verify transport length numbers are stripped and JSON arrays are formatted cleanly
        assertTrue(!cleanedBody.contains("16\n[["))
        assertTrue(!cleanedBody.contains("]]]99"))
        assertTrue(cleanedBody.contains("\"targetId\": 10"))
    }

    @Test
    fun testBinaryGrpcWebStreamBadge() {
        val tx = createTx(
            resHeaders = mapOf("content-type" to "application/grpc-web+proto")
        )

        assertEquals("gRPC Stream", tx.responseContentTypeBadge)
    }

    @Test
    fun testServerSentEventsSseStreamBadge() {
        val sseBody = """
            event: user_connected
            data: {"userId": "usr_99812", "timestamp": 178498000}
            
            data: {"userId": "usr_99813", "timestamp": 178498005}
        """.trimIndent()

        val tx = createTx(
            resHeaders = mapOf("content-type" to "text/event-stream"),
            resBody = sseBody
        )

        assertEquals("SSE Stream", tx.responseContentTypeBadge)
    }

    @Test
    fun testNdjsonMultiLineStreamBadge() {
        val ndjsonBody = """
            {"id": 1, "status": "active"}
            {"id": 2, "status": "pending"}
            {"id": 3, "status": "completed"}
        """.trimIndent()

        val tx = createTx(resBody = ndjsonBody)

        assertEquals("JSON Stream", tx.responseContentTypeBadge)
    }

    @Test
    fun testFormUrlEncodedBodyBadgeAndFormatting() {
        val formBody = "app=com.google.android.gms&device=5575004981882875345&sender=745476177629"

        val tx = createTx(
            reqHeaders = mapOf("content-type" to "application/x-www-form-urlencoded"),
            reqBody = formBody
        )

        assertEquals("Form Data", tx.requestContentTypeBadge)

        val formatted = prettyPrintBody(formBody)
        assertTrue(formatted.contains("app = com.google.android.gms"))
        assertTrue(formatted.contains("device = 5575004981882875345"))
        assertTrue(formatted.contains("sender = 745476177629"))
    }
    @Test
    fun testSinglePlainTextResponseNotBadgedAsFormData() {
        val plainText = "Error=DEPRECATED_ENDPOINT"
        val tx = createTx(
            resHeaders = mapOf("content-type" to "text/plain"),
            resBody = plainText
        )

        assertEquals("PLAIN", tx.responseContentTypeBadge)
    }

    @Test
    fun testStandardJsonBadge() {
        val jsonBody = """{"status":"success","code":200}"""
        val tx = createTx(
            resHeaders = mapOf("content-type" to "application/json"),
            resBody = jsonBody
        )

        assertEquals("JSON", tx.responseContentTypeBadge)
    }

    @Test
    fun testJsonContainingEventSubstringsIsBadgedAsJsonNotSse() {
        val payload = """{"update":{"html":{"val":"<div aria-expanded=\"false\" role=\"button\">event: click</div>"}}}"""
        val tx = createTx(resBody = payload)

        assertEquals("JSON", tx.responseContentTypeBadge)
    }

    @Test
    fun testImageBadge() {
        val tx = createTx(
            resHeaders = mapOf("content-type" to "image/png")
        )

        assertEquals("PNG Image", tx.responseContentTypeBadge)
    }

    @Test
    fun testProtobufBadge() {
        val tx = createTx(
            resHeaders = mapOf("content-type" to "application/x-protobuf")
        )
        assertEquals("Protobuf", tx.responseContentTypeBadge)
    }

    @Test
    fun testGoogleXssiPrefixStrippingAndFormatting() {
        val googleXssiPayload = ")]}'\n{\"response\":{\"server\":\"prod\",\"status\":\"ok\"}}"
        val formatted = prettyPrintBody(googleXssiPayload)

        assertTrue(formatted.startsWith("{"))
        assertTrue(!formatted.startsWith(")"))
        assertTrue(formatted.contains("\"response\": {"))
        assertTrue(formatted.contains("\"server\": \"prod\""))
        assertTrue(formatted.contains("\"status\": \"ok\""))
    }

    @Test
    fun testBinaryDescriptorNotFormattedAsJsonArray() {
        val binaryDescriptor = "[Binary payload — 0.4 KB · application/x-protobuffer]"
        val tx = createTx(resBody = binaryDescriptor)

        assertEquals("Protobuf", tx.responseContentTypeBadge)

        val formatted = prettyPrintBody(binaryDescriptor)
        assertEquals(binaryDescriptor, formatted)
    }

    @Test
    fun testGoogleSearchSuggestXssiJavascriptHeaderBadgedAsJson() {
        val rawSearchSuggestBody = ")]}'\n[\"\",[\"bitsat iteration 4\",\"thiago almada\",\"katseye animals\"],[\"\",\"\",\"\"],[],{\"google:clientdata\":{\"bpc\":false}}]"
        val tx = createTx(
            resHeaders = mapOf("content-type" to "application/x-javascript; charset=UTF-8"),
            resBody = rawSearchSuggestBody
        )

        assertEquals("JS", tx.responseContentTypeBadge)

        val formatted = prettyPrintBody(rawSearchSuggestBody)
        assertTrue(formatted.startsWith("["))
        assertTrue(!formatted.startsWith(")"))
        assertTrue(formatted.contains("\"bitsat iteration 4\""))
    }
}
