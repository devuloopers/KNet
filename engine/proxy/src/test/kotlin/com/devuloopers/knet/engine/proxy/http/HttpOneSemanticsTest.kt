package com.devuloopers.knet.engine.proxy.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Focused qualification coverage for version-specific HTTP/1 wire rules. */
class HttpOneSemanticsTest {

    @Test
    fun `http one zero defaults closed but accepts both connection keep alive forms`() {
        val defaultRequest = request()
        val connectionRequest = request().apply {
            headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        }
        val legacyProxyConnectionRequest = request().apply {
            headers().set("Proxy-Connection", "keep-alive")
        }

        assertFalse(HttpOneSemantics.downstreamPolicy(defaultRequest).persistenceRequested)
        assertTrue(HttpOneSemantics.downstreamPolicy(connectionRequest).persistenceRequested)
        assertTrue(HttpOneSemantics.downstreamPolicy(legacyProxyConnectionRequest).persistenceRequested)
    }

    @Test
    fun `http one zero transfer encoding is rejected before forwarding`() {
        val request = request().apply {
            headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        }

        assertEquals(
            HttpOneRequestViolation.HTTP_1_0_TRANSFER_ENCODING,
            HttpOneSemantics.validateRequest(request),
        )
        assertNull(HttpOneSemantics.validateRequest(request(HttpVersion.HTTP_1_1)))
    }

    @Test
    fun `legacy proxy connection is translated for the origin`() {
        val request = request().apply {
            headers().set("Proxy-Connection", "keep-alive")
        }
        val policy = HttpOneSemantics.downstreamPolicy(request)

        HttpOneSemantics.prepareUpstreamRequest(request, policy)

        assertFalse(request.headers().contains("Proxy-Connection"))
        assertEquals("keep-alive", request.headers().get(HttpHeaderNames.CONNECTION))
    }

    @Test
    fun `streamed chunked response becomes close delimited http one zero`() {
        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK).apply {
            headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
            headers().set(HttpHeaderNames.TRAILER, "X-Origin-Trailer")
        }

        val keepAlive = HttpOneSemantics.prepareFinalResponse(
            response = response,
            requestMethod = HttpMethod.GET,
            policy = HttpOneDownstreamPolicy(HttpVersion.HTTP_1_0, persistenceRequested = true),
        )

        assertFalse(keepAlive)
        assertEquals(HttpVersion.HTTP_1_0, response.protocolVersion())
        assertFalse(response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING))
        assertFalse(response.headers().contains(HttpHeaderNames.TRAILER))
        assertEquals("close", response.headers().get(HttpHeaderNames.CONNECTION))
    }

    @Test
    fun `complete response gains content length and can keep http one zero alive`() {
        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer("body".toByteArray()),
        ).apply {
            headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        }

        val keepAlive = HttpOneSemantics.prepareFinalResponse(
            response = response,
            requestMethod = HttpMethod.GET,
            policy = HttpOneDownstreamPolicy(HttpVersion.HTTP_1_0, persistenceRequested = true),
        )

        assertTrue(keepAlive)
        assertEquals("4", response.headers().get(HttpHeaderNames.CONTENT_LENGTH))
        assertFalse(response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING))
        assertEquals("keep-alive", response.headers().get(HttpHeaderNames.CONNECTION))
        response.release()
    }

    @Test
    fun `http one zero drops response trailers`() {
        val content = DefaultLastHttpContent(Unpooled.EMPTY_BUFFER).apply {
            trailingHeaders().set("X-Origin-Trailer", "value")
        }

        HttpOneSemantics.prepareFinalContent(
            content,
            HttpOneDownstreamPolicy(HttpVersion.HTTP_1_0, persistenceRequested = false),
        )

        assertTrue(content.trailingHeaders().isEmpty)
        content.release()
    }

    private fun request(version: HttpVersion = HttpVersion.HTTP_1_0): DefaultHttpRequest =
        DefaultHttpRequest(version, HttpMethod.GET, "http://example.test/")
}
