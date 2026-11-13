package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.traffic.model.TrafficTransferCounter
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import kotlin.test.*

/** Real pipeline ordering verifies only successful writes count and buffers are never retained by metering. */
class SourceTrafficMeterHandlerTest {
    @Test fun `inbound and encoded outbound bytes count once without retaining buffers`() {
        val counter = Counter()
        val channel = EmbeddedChannel(SourceTrafficMeterHandler(counter))
        try {
            val uploaded = Unpooled.wrappedBuffer(ByteArray(100))
            channel.writeInbound(uploaded)
            assertEquals(100, counter.up)
            assertEquals(1, uploaded.refCnt())
            ReferenceCountUtil.release(channel.readInbound<Any>())
            val downloaded = Unpooled.wrappedBuffer(ByteArray(250))
            channel.writeOutbound(downloaded)
            assertEquals(250, counter.down)
            assertEquals(1, downloaded.refCnt())
            ReferenceCountUtil.release(channel.readOutbound<Any>())
            channel.close(); channel.close()
            assertEquals(1, counter.closes)
        } finally { channel.finishAndReleaseAll() }
    }
    @Test fun `failed writes never count as delivered download`() {
        val counter = Counter()
        val reject = object : ChannelOutboundHandlerAdapter() {
            override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
                ReferenceCountUtil.release(message); promise.setFailure(java.io.IOException("test write failed"))
            }
        }
        val channel = EmbeddedChannel(reject, SourceTrafficMeterHandler(counter))
        try {
            val buffer = Unpooled.wrappedBuffer(ByteArray(10))
            assertFails { channel.writeOutbound(buffer) }
            assertEquals(0, counter.down)
            assertEquals(0, buffer.refCnt())
        } finally { channel.finishAndReleaseAll() }
    }
    private class Counter : TrafficTransferCounter {
        var up = 0L; var down = 0L; var closes = 0
        override fun uploaded(bytes: Long) { up += bytes }
        override fun downloaded(bytes: Long) { down += bytes }
        override fun close() { closes++ }
    }
}
