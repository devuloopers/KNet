package com.devuloopers.knet.engine.proxy.mapper

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HttpMapperTest {

    @Test
    fun testMapRequestFromNettyToDomainModel() {
        val bodyContent = "{\"user\":\"alice\"}".toByteArray(StandardCharsets.UTF_8)
        val nettyReq = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/api/v1/user",
            Unpooled.wrappedBuffer(bodyContent)
        )
        nettyReq.headers().set("Content-Type", "application/json")

        val domainReq = HttpMapper.mapRequest(nettyReq, "httpbin.org", isSsl = true)

        assertNotNull(domainReq.id)
        assertEquals("POST", domainReq.method)
        assertEquals("https://httpbin.org/api/v1/user", domainReq.url)
        assertEquals("HTTP/1.1", domainReq.protocol)
        assertNotNull(domainReq.body)
        assertEquals("{\"user\":\"alice\"}", String(domainReq.body!!, StandardCharsets.UTF_8))
    }

    @Test
    fun testMapResponseFromNettyToDomainModel() {
        val bodyContent = "OK".toByteArray(StandardCharsets.UTF_8)
        val nettyRes = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bodyContent)
        )

        val domainRes = HttpMapper.mapResponse(nettyRes)

        assertEquals(200, domainRes.statusCode)
        assertEquals("OK", domainRes.statusText)
        assertNotNull(domainRes.body)
        assertEquals("OK", String(domainRes.body!!, StandardCharsets.UTF_8))
    }
}
