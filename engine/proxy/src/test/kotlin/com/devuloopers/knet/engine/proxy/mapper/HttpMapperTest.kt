package com.devuloopers.knet.engine.proxy.mapper

import com.devuloopers.knet.traffic.model.TrafficAttributionHeader
import com.devuloopers.knet.traffic.model.TrafficOrigin
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HttpMapperTest {

    @Test
    fun mapsNettyRequestToCanonicalContext() {
        val nettyReq = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/api/v1/user",
            Unpooled.EMPTY_BUFFER,
        )
        nettyReq.headers().set("Content-Type", "application/json")

        val context = HttpMapper.mapRequestContext(
            nettyReq = nettyReq,
            isSsl = true,
            host = "httpbin.org",
            port = 443,
            relativeUri = "/api/v1/user",
        )

        assertNotNull(context.exchangeId.value)
        assertEquals("POST", context.request.head.method.token)
        assertEquals("HTTP/1.1", context.request.head.protocol.token)
        assertEquals("application/json", context.request.head.headers.single().value)
    }

    @Test
    fun mapsNettyResponseToCanonicalHead() {
        val nettyRes = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.EMPTY_BUFFER,
        )

        val responseHead = HttpMapper.mapResponseHead(nettyRes)

        assertEquals(200, responseHead.status.code)
        assertEquals("OK", responseHead.reasonPhrase)
        assertEquals("HTTP/1.1", responseHead.protocol.token)
    }

    @Test
    fun `consumes API Studio attribution without exposing it as captured or upstream metadata`() {
        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/graphql",
            Unpooled.EMPTY_BUFFER,
        )
        request.headers().set(TrafficAttributionHeader.NAME, TrafficOrigin.ApiStudio.token)

        val context = HttpMapper.mapRequestContext(
            nettyReq = request,
            isSsl = true,
            host = "api.knet.dev",
            port = 443,
            relativeUri = "/graphql",
        )
        HttpMapper.removeCaptureAttribution(request)

        assertEquals(TrafficOrigin.ApiStudio, context.origin)
        assertEquals(
            false,
            context.request.head.headers.any {
                it.name.value.equals(TrafficAttributionHeader.NAME, ignoreCase = true)
            },
        )
        assertEquals(false, request.headers().contains(TrafficAttributionHeader.NAME))
    }
}
