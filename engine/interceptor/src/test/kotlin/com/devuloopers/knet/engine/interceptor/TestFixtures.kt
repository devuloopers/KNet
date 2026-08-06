package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion

object TestFixtures {

    fun createHttpRequestDto(
        url: String = "https://api.example.com/v1/users",
        method: String = "GET",
        headers: List<Pair<String, String>> = listOf("User-Agent" to "KNetTest"),
        body: String? = null
    ): HttpRequest {
        return HttpRequest(
            id = "req-1",
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
        headers: List<Pair<String, String>> = listOf("Content-Type" to "application/json"),
        body: String? = """{"status":"ok"}"""
    ): HttpResponse {
        return HttpResponse(
            statusCode = statusCode,
            statusText = "OK",
            headers = headers,
            body = body?.toByteArray(Charsets.UTF_8),
            timestamp = System.currentTimeMillis()
        )
    }

    fun createFullHttpRequest(
        uri: String = "https://api.example.com/v1/users",
        method: HttpMethod = HttpMethod.GET,
        body: String = ""
    ): FullHttpRequest {
        val content = Unpooled.copiedBuffer(body, Charsets.UTF_8)
        val req = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, content)
        req.headers().set(HttpHeaderNames.HOST, "api.example.com")
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return req
    }

    fun createFullHttpResponse(
        status: HttpResponseStatus = HttpResponseStatus.OK,
        body: String = """{"status":"ok"}"""
    ): FullHttpResponse {
        val content = Unpooled.copiedBuffer(body, Charsets.UTF_8)
        val res = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content)
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return res
    }
}
