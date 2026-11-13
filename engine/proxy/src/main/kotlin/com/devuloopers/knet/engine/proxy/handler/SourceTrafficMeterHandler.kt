package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.traffic.model.TrafficTransferCounter
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise

/** Counts real wire observations before decoding and successful writes after encoding, with no byte retention. */
internal class SourceTrafficMeterHandler(private val counter: TrafficTransferCounter) : ChannelDuplexHandler() {
    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        if (message is ByteBuf) counter.uploaded(message.readableBytes().toLong())
        context.fireChannelRead(message)
    }

    override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
        if (message is ByteBuf) {
            val size = message.readableBytes().toLong()
            val tracked = promise.unvoid()
            tracked.addListener { result -> if (result.isSuccess) counter.downloaded(size) }
            context.write(message, tracked)
        } else context.write(message, promise)
    }

    override fun channelInactive(context: ChannelHandlerContext) { counter.close(); context.fireChannelInactive() }
}
