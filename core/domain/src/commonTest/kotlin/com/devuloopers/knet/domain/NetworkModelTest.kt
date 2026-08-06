package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkModelTest {

    @Test
    fun testHttpRequestCreationAndProperties() {
        val req = HttpRequest(
            id = "req-1",
            method = "POST",
            url = "https://api.knet.dev/data",
            protocol = "HTTP/2",
            headers = listOf("Content-Type" to "application/json"),
            body = "{\"test\":true}".encodeToByteArray(),
            timestamp = 1000L
        )

        assertEquals("req-1", req.id)
        assertEquals("POST", req.method)
        assertEquals("https://api.knet.dev/data", req.url)
        assertEquals("HTTP/2", req.protocol)
        assertEquals(1, req.headers.size)
        assertEquals("Content-Type", req.headers[0].first)
        assertEquals("application/json", req.headers[0].second)
        assertNotNull(req.body)
        assertContentEquals("{\"test\":true}".encodeToByteArray(), req.body)
        assertEquals(1000L, req.timestamp)
    }

    @Test
    fun testHttpResponseCreationAndProperties() {
        val res = HttpResponse(
            statusCode = 201,
            statusText = "Created",
            headers = listOf("Location" to "/data/1"),
            body = "{\"id\":1}".encodeToByteArray(),
            timestamp = 1200L
        )

        assertEquals(201, res.statusCode)
        assertEquals("Created", res.statusText)
        assertEquals(1, res.headers.size)
        assertEquals("Location", res.headers[0].first)
        assertEquals("/data/1", res.headers[0].second)
        assertNotNull(res.body)
        assertEquals(1200L, res.timestamp)
    }

    @Test
    fun testHttpTimingsTotalTimeCalculation() {
        val defaultTimings = HttpTimings()
        assertEquals(0L, defaultTimings.dnsMs)
        assertEquals(0L, defaultTimings.tcpMs)
        assertEquals(0L, defaultTimings.tlsMs)
        assertEquals(0L, defaultTimings.ttfbMs)
        assertEquals(0L, defaultTimings.downloadMs)
        assertEquals(0L, defaultTimings.totalTimeMs)
        assertFalse(defaultTimings.hasRealTimings)

        val activeTimings = HttpTimings(
            dnsMs = 15L,
            tcpMs = 25L,
            tlsMs = 45L,
            ttfbMs = 150L,
            downloadMs = 65L
        )

        assertEquals(300L, activeTimings.totalTimeMs)
        assertTrue(activeTimings.hasRealTimings)
    }

    @Test
    fun testHttpTransactionEqualityAndCopy() {
        val req = TestFixtures.createHttpRequest()
        val res = TestFixtures.createHttpResponse()
        val tx1 = TestFixtures.createHttpTransaction(id = "tx-1", request = req, response = res)
        val tx2 = TestFixtures.createHttpTransaction(id = "tx-1", request = req, response = res)

        assertEquals(tx1, tx2)
        assertEquals(tx1.hashCode(), tx2.hashCode())

        val copiedTx = tx1.copy(durationMs = 999L)
        assertEquals("tx-1", copiedTx.id)
        assertEquals(999L, copiedTx.durationMs)
        assertEquals(200L, tx1.durationMs)
    }

    @Test
    fun testHttpTransactionWithoutResponse() {
        val req = TestFixtures.createHttpRequest()
        val tx = HttpTransaction(
            id = "tx-pending",
            request = req,
            response = null,
            requestBodyPath = null,
            responseBodyPath = null,
            durationMs = 0L,
            timestamp = 1000L
        )

        assertNull(tx.response)
        assertNull(tx.requestBodyPath)
        assertNull(tx.responseBodyPath)
        assertEquals(0L, tx.durationMs)
    }
}
