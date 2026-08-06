package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionIntegrationTest {

    @Test
    fun testProxyTrafficListenerSessionCaptureEvents() {
        var capturedReq: HttpRequest? = null
        var capturedRes: HttpResponse? = null

        val listener = object : ProxyTrafficListener {
            override fun onRequestCaptured(request: HttpRequest) {
                capturedReq = request
            }

            override fun onResponseCaptured(
                transactionId: String,
                response: HttpResponse,
                durationMs: Long,
                timings: HttpTimings
            ) {
                capturedRes = response
            }
        }

        val dummyReq = HttpRequest(
            id = "tx_session_1",
            method = "GET",
            url = "https://httpbin.org/get",
            protocol = "HTTP/1.1",
            headers = emptyList(),
            body = null,
            timestamp = System.currentTimeMillis()
        )

        val dummyRes = HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = emptyList(),
            body = null,
            timestamp = System.currentTimeMillis()
        )

        listener.onRequestCaptured(dummyReq)
        assertNotNull(capturedReq)
        assertEquals("tx_session_1", capturedReq?.id)

        listener.onResponseCaptured("tx_session_1", dummyRes, 45L, HttpTimings())
        assertNotNull(capturedRes)
        assertEquals(200, capturedRes?.statusCode)
    }
}
