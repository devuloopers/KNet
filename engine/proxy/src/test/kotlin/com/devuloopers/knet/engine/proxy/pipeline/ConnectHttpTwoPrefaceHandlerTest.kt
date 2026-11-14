package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.ChannelInputShutdownEvent
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises every byte boundary without involving TLS or an HTTP decoder in classification. */
class ConnectHttpTwoPrefaceHandlerTest {
    @Test
    fun `every preface split and coalesced following frames replay exactly once`() {
        for (split in 1..PREFACE.size) {
            var selections = 0
            val received = mutableListOf<Byte>()
            val channel = EmbeddedChannel(ConnectHttpTwoPrefaceHandler(100) { context ->
                selections++
                context.pipeline().addAfter(context.name(), "selected", recorder(received))
            })
            try {
                channel.writeInbound(Unpooled.wrappedBuffer(PREFACE.copyOfRange(0, split)))
                if (split < PREFACE.size) assertEquals(0, selections)
                channel.writeInbound(Unpooled.wrappedBuffer(PREFACE.copyOfRange(split, PREFACE.size) + FOLLOWING))
                assertEquals(1, selections)
                assertContentEquals(PREFACE + FOLLOWING, received.toByteArray())
                channel.advanceTimeBy(101, TimeUnit.MILLISECONDS)
                channel.runScheduledPendingTasks()
                assertTrue(channel.isActive, "Selected protocol inherited the cancelled setup timer")
            } finally { channel.finishAndReleaseAll() }
        }
    }

    @Test
    fun `one byte at a time and consecutive content never lose or duplicate bytes`() {
        val received = mutableListOf<Byte>()
        var selections = 0
        val channel = EmbeddedChannel(ConnectHttpTwoPrefaceHandler(100) { context ->
            selections++
            context.pipeline().addAfter(context.name(), "selected", recorder(received))
        })
        try {
            (PREFACE + FOLLOWING).forEach { value -> channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(value))) }
            assertEquals(1, selections)
            assertContentEquals(PREFACE + FOLLOWING, received.toByteArray())
        } finally { channel.finishAndReleaseAll() }
    }

    @Test
    fun `TLS and mismatched PRI bytes pass unchanged to the original handler`() {
        for (bytes in listOf(byteArrayOf(0x16, 3, 3, 0, 4), "PRI * HTTP/2.0\r\n\r\nXX\r\n\r\n".toByteArray())) {
            val received = mutableListOf<Byte>()
            val channel = EmbeddedChannel(
                ConnectHttpTwoPrefaceHandler(100) { error("Non-H2 data selected H2") },
                recorder(received),
            )
            try {
                channel.writeInbound(Unpooled.wrappedBuffer(bytes))
                assertContentEquals(bytes, received.toByteArray())
                channel.advanceTimeBy(101, TimeUnit.MILLISECONDS)
                channel.runScheduledPendingTasks()
                assertTrue(channel.isActive)
            } finally { channel.finishAndReleaseAll() }
        }
    }

    @Test
    fun `partial preface deadline is absolute even while bytes trickle in`() {
        val channel = EmbeddedChannel(ConnectHttpTwoPrefaceHandler(100) { error("Partial preface selected H2") })
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(PREFACE.copyOfRange(0, 5)))
            channel.advanceTimeBy(60, TimeUnit.MILLISECONDS)
            channel.writeInbound(Unpooled.wrappedBuffer(PREFACE.copyOfRange(5, 10)))
            channel.advanceTimeBy(41, TimeUnit.MILLISECONDS)
            channel.runScheduledPendingTasks()
            assertFalse(channel.isActive)
        } finally { channel.finishAndReleaseAll() }
    }

    @Test
    fun `EOF on every incomplete prefix immediately closes a half closed transport`() {
        for (size in 0 until PREFACE.size) {
            val channel = EmbeddedChannel(ConnectHttpTwoPrefaceHandler(100) { error("Partial preface selected H2") })
            try {
                if (size > 0) channel.writeInbound(Unpooled.wrappedBuffer(PREFACE.copyOf(size)))
                channel.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
                assertFalse(channel.isActive, "Retained incomplete preface of $size bytes")
            } finally { channel.finishAndReleaseAll() }
        }
    }

    private fun recorder(received: MutableList<Byte>) = object : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            val buffer = message as ByteBuf
            try { while (buffer.isReadable) received += buffer.readByte() } finally { buffer.release() }
        }
    }

    private companion object {
        val PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray()
        val FOLLOWING = byteArrayOf(0, 0, 0, 4, 0, 0, 0, 0, 0)
    }
}
