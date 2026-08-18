package com.devuloopers.knet.engine.proxy.handler

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KNetOutboundHandlerTest {

    @Test
    fun testOutboundHandlerRelaysResponseToClientChannel() {
        val clientChannel = EmbeddedChannel()
        val dummyRequest = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
        val outboundHandler = KNetOutboundHandler(
            clientChannel = clientChannel,
            request = dummyRequest,
        )
        val serverChannel = EmbeddedChannel(outboundHandler)

        val inboundResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        serverChannel.writeInbound(inboundResponse)

        val relayedResponse = clientChannel.readOutbound<DefaultFullHttpResponse>()
        assertNotNull(relayedResponse)
        assertEquals(HttpResponseStatus.OK, relayedResponse.status())
        clientChannel.close()
        serverChannel.close()
    }
}
