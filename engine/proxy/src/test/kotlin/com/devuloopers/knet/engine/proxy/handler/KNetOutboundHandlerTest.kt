package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.HttpConversionUtil
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
                onExchangeComplete = { _ -> completionCount += 1 },
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

    @Test
    fun `captures upstream response protocol before adapting it for an HTTP 1_0 client`() {
        val clientChannel = EmbeddedChannel()
        val capture = RecordingExchangeCapture()
        val serverChannel = EmbeddedChannel(
            KNetOutboundHandler(
                clientChannel = clientChannel,
                request = DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/"),
                capture = capture,
            ),
        )

        serverChannel.writeInbound(
            DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK),
        )

        val relayedResponse = clientChannel.readOutbound<DefaultFullHttpResponse>()
        assertEquals(HttpVersion.HTTP_1_0, relayedResponse.protocolVersion())
        assertEquals(
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
            capture.response?.protocol,
        )
        relayedResponse.release()
        clientChannel.finishAndReleaseAll()
        serverChannel.finishAndReleaseAll()
    }

    @Test
    fun `does not expose upstream HTTP two bridge headers to capture or downstream clients`() {
        val clientChannel = EmbeddedChannel()
        val capture = RecordingExchangeCapture()
        val serverChannel = EmbeddedChannel(
            KNetOutboundHandler(
                clientChannel = clientChannel,
                request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                capture = capture,
                upstreamProtocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2),
            ),
        )
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK).apply {
            headers().set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), "3")
            headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https")
            headers().set("Content-Type", "application/json")
        }

        serverChannel.writeInbound(response)

        val relayedResponse = clientChannel.readOutbound<DefaultFullHttpResponse>()
        assertEquals(false, relayedResponse.headers().contains(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()))
        assertEquals(false, relayedResponse.headers().contains(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text()))
        assertEquals("application/json", relayedResponse.headers().get("Content-Type"))
        assertEquals(
            listOf("Content-Type"),
            capture.response?.headers?.map { header -> header.name.value },
        )
        relayedResponse.release()
        clientChannel.finishAndReleaseAll()
        serverChannel.finishAndReleaseAll()
    }

    private class RecordingExchangeCapture : ProxyExchangeCapture {
        override val exchangeId: ExchangeId = ExchangeId("test-exchange")
        var response: ResponseHead? = null

        override fun tryReserveBody(
            direction: TrafficDirection,
            contentEncoding: ContentEncoding?,
            requestedBytes: Int,
        ): ProxyBodyReservation? = null

        override fun completeBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
        ) = Unit

        override fun cancelBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
            reason: TrafficTerminationReason,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
            this.response = response
        }

        override fun terminate(
            outcome: ExchangeTerminalOutcome,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
        ) = Unit
    }
}
