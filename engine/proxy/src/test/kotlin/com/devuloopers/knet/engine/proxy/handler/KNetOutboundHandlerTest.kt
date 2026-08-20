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
        relayedResponse.release()
        clientChannel.close()
        serverChannel.close()
    }

    @Test
    fun `upstream close before response returns bad gateway and completes ownership`() {
        val clientChannel = EmbeddedChannel()
        var completionCount = 0
        val serverChannel = EmbeddedChannel(
            KNetOutboundHandler(
                clientChannel = clientChannel,
                request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                onExchangeComplete = { completionCount += 1 },
            ),
        )

        serverChannel.close()

        val response = clientChannel.readOutbound<DefaultFullHttpResponse>()
        assertNotNull(response)
        assertEquals(HttpResponseStatus.BAD_GATEWAY, response.status())
        assertEquals(1, completionCount)
        response.release()
        clientChannel.finishAndReleaseAll()
        serverChannel.finishAndReleaseAll()
    }
}
