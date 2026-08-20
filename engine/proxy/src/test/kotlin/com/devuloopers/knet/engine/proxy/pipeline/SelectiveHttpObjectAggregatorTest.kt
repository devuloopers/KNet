package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SelectiveHttpObjectAggregatorTest {
    @Test
    fun `unselected message stays incremental`() {
        val channel = EmbeddedChannel(SelectiveHttpObjectAggregator(8) { _, _ -> false })

        channel.writeInbound(DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload"))
        channel.writeInbound(DefaultLastHttpContent(Unpooled.wrappedBuffer("body".encodeToByteArray())))

        assertIs<HttpRequest>(channel.readInbound<Any>())
        ReferenceCountUtil.release(channel.readInbound<HttpContent>())
        channel.finishAndReleaseAll()
    }

    @Test
    fun `selected message within limit becomes one full message`() {
        val channel = EmbeddedChannel(SelectiveHttpObjectAggregator(8) { _, _ -> true })

        channel.writeInbound(DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/edit"))
        channel.writeInbound(DefaultHttpContent(Unpooled.wrappedBuffer("bo".encodeToByteArray())))
        channel.writeInbound(DefaultLastHttpContent(Unpooled.wrappedBuffer("dy".encodeToByteArray())))

        val full = channel.readInbound<FullHttpRequest>()
        assertEquals("body", full.content().toString(Charsets.UTF_8))
        full.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `selected message crossing limit replays and continues as a stream`() {
        val channel = EmbeddedChannel(SelectiveHttpObjectAggregator(3) { _, _ -> true })

        channel.writeInbound(DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/large"))
        channel.writeInbound(DefaultHttpContent(Unpooled.wrappedBuffer("ab".encodeToByteArray())))
        channel.writeInbound(DefaultLastHttpContent(Unpooled.wrappedBuffer("cd".encodeToByteArray())))

        assertIs<HttpRequest>(channel.readInbound<Any>())
        val first = channel.readInbound<HttpContent>()
        val last = channel.readInbound<HttpContent>()
        assertFalse(first is FullHttpRequest)
        assertEquals("ab", first.content().toString(Charsets.UTF_8))
        assertEquals("cd", last.content().toString(Charsets.UTF_8))
        first.release()
        last.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `selected response uses the same protocol-neutral aggregation path`() {
        val channel = EmbeddedChannel(SelectiveHttpObjectAggregator(8) { _, _ -> true })

        channel.writeInbound(DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
        channel.writeInbound(DefaultLastHttpContent(Unpooled.wrappedBuffer("body".encodeToByteArray())))

        val full = channel.readInbound<FullHttpResponse>()
        assertEquals(HttpResponseStatus.OK, full.status())
        assertEquals("body", full.content().toString(Charsets.UTF_8))
        full.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `already-full selected message crossing limit is decomposed for streaming`() {
        val channel = EmbeddedChannel(SelectiveHttpObjectAggregator(3) { _, _ -> true })
        val original = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/large",
            Unpooled.wrappedBuffer("body".encodeToByteArray()),
        )

        channel.writeInbound(original)

        assertIs<HttpRequest>(channel.readInbound<Any>())
        val last = channel.readInbound<HttpContent>()
        assertEquals("body", last.content().toString(Charsets.UTF_8))
        // The streamed tail owns the retained duplicate until its downstream consumer releases it.
        assertEquals(1, original.refCnt())
        last.release()
        assertEquals(0, original.refCnt())
        channel.finishAndReleaseAll()
    }
}
