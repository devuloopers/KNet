package com.devuloopers.knet.engine.traffic

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import java.io.File

/**
 * Reusable test fixtures for Traffic Engine unit & integration tests.
 */
object TestFixtures {

    fun createHttpRequest(
        uri: String = "https://api.example.com/users",
        method: HttpMethod = HttpMethod.GET,
        body: String = "",
        contentType: String = "application/json"
    ): FullHttpRequest {
        val buf = Unpooled.copiedBuffer(body, Charsets.UTF_8)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, buf)
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length)
        return request
    }

    fun createHttpResponse(
        status: HttpResponseStatus = HttpResponseStatus.OK,
        body: String = """{"status":"success"}""",
        contentType: String = "application/json"
    ): FullHttpResponse {
        val buf = Unpooled.copiedBuffer(body, Charsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length)
        return response
    }

    fun createTempFile(content: String = """{"mock": true}""", suffix: String = ".json"): File {
        val file = File.createTempFile("traffic_test_", suffix)
        file.writeText(content)
        file.deleteOnExit()
        return file
    }
}
