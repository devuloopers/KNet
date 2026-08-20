package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointBodyEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod as CanonicalHttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
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

    fun createRequestEdit(
        url: String = "https://api.example.com/v1/users",
        method: String = "GET",
        headers: List<Pair<String, String>> = listOf("User-Agent" to "KNetTest"),
        body: String? = null
    ): BreakpointRequestEdit {
        val path = url.substringAfter("api.example.com", "/")
        return BreakpointRequestEdit(
            request = HttpRequestSnapshot(
                head = RequestHead(
                    method = CanonicalHttpMethod.fromToken(method),
                    target = RequestTarget.Absolute(
                        scheme = HttpScheme.fromToken("https"),
                        authority = Authority("api.example.com", 443),
                        pathAndQuery = path,
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = headers.map { (name, value) -> HeaderField(HeaderName(name), value) },
                ),
            ),
            body = body?.encodeToByteArray()
                ?.let(::BreakpointBody)
                ?.let(BreakpointBodyEdit::Replace)
                ?: BreakpointBodyEdit.Unchanged,
        )
    }

    fun createResponseEdit(
        statusCode: Int = 200,
        headers: List<Pair<String, String>> = listOf("Content-Type" to "application/json"),
        body: String? = """{"status":"ok"}"""
    ): BreakpointResponseEdit {
        return BreakpointResponseEdit(
            response = HttpResponseSnapshot(
                head = ResponseHead(
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    status = HttpStatus(statusCode),
                    reasonPhrase = if (statusCode == 200) "OK" else null,
                    headers = headers.map { (name, value) -> HeaderField(HeaderName(name), value) },
                ),
            ),
            body = body?.encodeToByteArray()
                ?.let(::BreakpointBody)
                ?.let(BreakpointBodyEdit::Replace)
                ?: BreakpointBodyEdit.Unchanged,
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
