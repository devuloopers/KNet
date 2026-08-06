package com.devuloopers.knet.engine.session

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import java.io.File

object TestFixtures {

    fun createHttpRequestDto(
        id: String = "req-1",
        url: String = "https://api.example.com/v1/users",
        method: String = "GET",
        headers: List<Pair<String, String>> = listOf("User-Agent" to "KNetTest"),
        body: String? = null
    ): HttpRequest {
        return HttpRequest(
            id = id,
            method = method,
            url = url,
            protocol = "HTTP/1.1",
            headers = headers,
            body = body?.toByteArray(Charsets.UTF_8),
            timestamp = System.currentTimeMillis()
        )
    }

    fun createHttpResponseDto(
        statusCode: Int = 200,
        statusText: String = "OK",
        headers: List<Pair<String, String>> = listOf("Content-Type" to "application/json"),
        body: String? = """{"status":"ok"}"""
    ): HttpResponse {
        return HttpResponse(
            statusCode = statusCode,
            statusText = statusText,
            headers = headers,
            body = body?.toByteArray(Charsets.UTF_8),
            timestamp = System.currentTimeMillis()
        )
    }

    fun createHttpTransaction(
        id: String = "tx-1",
        request: HttpRequest = createHttpRequestDto(id = id),
        response: HttpResponse? = createHttpResponseDto(),
        durationMs: Long = 120L
    ): HttpTransaction {
        return HttpTransaction(
            id = id,
            request = request,
            response = response,
            requestBodyPath = null,
            responseBodyPath = null,
            durationMs = durationMs,
            timestamp = System.currentTimeMillis(),
            timings = HttpTimings(dnsMs = 10, tcpMs = 20, tlsMs = 30, ttfbMs = 40, downloadMs = 20)
        )
    }

    fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "knet_session_test_${System.currentTimeMillis()}")
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }
}
