package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.handler.codec.http.*
import java.util.concurrent.CompletableFuture
import kotlin.test.*

/** Upstream EOF resolves incomplete responses immediately without discarding accepted transforms. */
class UpstreamEofLifecycleTest {
    @Test fun `EOF without response headers produces 502 and releases ownership`() {
        val client = EmbeddedChannel()
        var completions = 0
        val server = EmbeddedChannel(KNetOutboundHandler(client,
            DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"), onExchangeComplete = { completions++ }))
        try {
            server.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
            val response = assertNotNull(client.readOutbound<FullHttpResponse>())
            try { assertEquals(502, response.status().code()) } finally { response.release() }
            assertEquals(1, completions)
            assertFalse(server.isActive)
        } finally { server.finishAndReleaseAll(); client.finishAndReleaseAll() }
    }

    @Test fun `complete response transform survives upstream close and delivers its final body`() {
        val decision = CompletableFuture<ProxyStreamTransformResult>()
        val transformer = object : ProxyStreamTransformer {
            override fun transform(direction: TrafficDirection, payload: ByteArray, endOfDirection: Boolean, occurredAtEpochMillis: Long) = decision
            override fun cancel(reason: TrafficTerminationReason?) = Unit
        }
        val client = EmbeddedChannel()
        var completions = 0
        val server = EmbeddedChannel(KNetOutboundHandler(client,
            DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
            streamTransformer = transformer, onExchangeComplete = { completions++ }))
        try {
            server.writeInbound(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(byteArrayOf(1))))
            server.close()
            assertEquals(0, completions)
            decision.complete(ProxyStreamTransformResult.Forward(byteArrayOf(9)))
            repeat(3) { server.runPendingTasks(); client.runPendingTasks() }
            assertNotNull(client.readOutbound<HttpResponse>())
            val content = assertNotNull(client.readOutbound<LastHttpContent>())
            try { assertEquals(9, content.content().readByte().toInt()) } finally { content.release() }
            assertEquals(1, completions)
        } finally { server.finishAndReleaseAll(); client.finishAndReleaseAll() }
    }
}
