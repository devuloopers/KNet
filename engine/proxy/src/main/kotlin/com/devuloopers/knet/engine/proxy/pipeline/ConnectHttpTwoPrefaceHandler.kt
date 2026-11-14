package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Distinguishes a complete cleartext HTTP/2 preface from a CONNECT stream's TLS bytes.
 *
 * No bytes are consumed or copied: removing this decoder transfers its cumulation exactly once
 * to the selected pipeline. A matching partial preface has an absolute setup deadline and EOF
 * closes the transport, so slow or truncated clients cannot retain an admission indefinitely.
 * [installHttpTwo] and [installTls] install their handlers after this decoder before it is removed.
 */
internal class ConnectHttpTwoPrefaceHandler(
    private val setupTimeoutMillis: Long,
    private val installTls: (ChannelHandlerContext) -> Unit = {},
    private val installHttpTwo: (ChannelHandlerContext) -> Unit,
) : ByteToMessageDecoder() {
    private var deadline: ScheduledFuture<*>? = null

    override fun handlerAdded(context: ChannelHandlerContext) {
        deadline = context.executor().schedule({ context.close() }, setupTimeoutMillis, TimeUnit.MILLISECONDS)
    }

    override fun decode(context: ChannelHandlerContext, input: ByteBuf, output: MutableList<Any>) {
        val length = minOf(input.readableBytes(), PREFACE.size)
        for (index in 0 until length) {
            if (input.getByte(input.readerIndex() + index) != PREFACE[index]) {
                // The ClientHello classifier receives every original TLS byte unchanged.
                installTls(context)
                context.pipeline().remove(this)
                return
            }
        }
        if (length < PREFACE.size) return
        installHttpTwo(context)
        context.pipeline().remove(this)
    }

    override fun decodeLast(context: ChannelHandlerContext, input: ByteBuf, output: MutableList<Any>) {
        if (input.isReadable) decode(context, input, output)
        if (!context.isRemoved) context.close()
    }

    override fun handlerRemoved0(context: ChannelHandlerContext) {
        deadline?.cancel(false)
        deadline = null
    }

    private companion object {
        val PREFACE: ByteArray = Http2CodecUtil.connectionPrefaceBuf().let { buffer ->
            try {
                ByteArray(buffer.readableBytes()).also(buffer::readBytes)
            } finally {
                buffer.release()
            }
        }
    }
}
