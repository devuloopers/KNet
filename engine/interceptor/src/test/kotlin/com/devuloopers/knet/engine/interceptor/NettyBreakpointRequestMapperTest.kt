package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.traffic.model.http.RequestTarget
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NettyBreakpointRequestMapperTest {
    @Test
    fun `breakpoint matching uses HTTP authority while retaining an IP tunnel route`() {
        var mapped: ProxyRequestContext? = null
        val channel = EmbeddedChannel(object : ChannelInboundHandlerAdapter() {
            override fun channelRead(context: ChannelHandlerContext, message: Any) {
                mapped = mapBreakpointRequest(context, message as DefaultHttpRequest)
            }
        })
        channel.attr(ProxyChannelAttributes.ROUTE_HOST).set("184.28.108.10")
        channel.attr(ProxyChannelAttributes.TLS_SERVER_NAME).set("stg-01astra.cnbc.com")
        channel.attr(ProxyChannelAttributes.PORT).set(8_443)
        channel.attr(ProxyChannelAttributes.IS_SSL).set(true)
        val request = DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/graphql").apply {
            headers().set(HttpHeaderNames.HOST, "stg-01astra.cnbc.com")
        }

        channel.writeInbound(request)

        val target = assertIs<RequestTarget.Absolute>(checkNotNull(mapped).request.head.target)
        assertEquals("stg-01astra.cnbc.com", target.authority.host)
        assertEquals(8_443, target.authority.port)
        channel.finishAndReleaseAll()
    }

    @Test
    fun `missing Host falls back to ClientHello SNI instead of route IP`() {
        var mapped: ProxyRequestContext? = null
        val channel = EmbeddedChannel(object : ChannelInboundHandlerAdapter() {
            override fun channelRead(context: ChannelHandlerContext, message: Any) {
                mapped = mapBreakpointRequest(context, message as DefaultHttpRequest)
            }
        })
        channel.attr(ProxyChannelAttributes.ROUTE_HOST).set("184.28.108.10")
        channel.attr(ProxyChannelAttributes.TLS_SERVER_NAME).set("stg-01astra.cnbc.com")
        channel.attr(ProxyChannelAttributes.PORT).set(443)
        channel.attr(ProxyChannelAttributes.IS_SSL).set(true)

        channel.writeInbound(DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/health"))

        val target = assertIs<RequestTarget.Absolute>(checkNotNull(mapped).request.head.target)
        assertEquals("stg-01astra.cnbc.com", target.authority.host)
        channel.finishAndReleaseAll()
    }
}
