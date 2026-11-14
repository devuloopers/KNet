package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.handler.ssl.SslCloseCompletionEvent

/**
 * Owns connection EOF after HTTP/2 selection. A peer can finish sending complete requests before
 * their responses arrive. Retain those children and their final writes, cancel incomplete requests,
 * then close the parent to release proxy/carrier admission. All state belongs to the parent loop.
 */
internal class HttpTwoInputDrainHandler : ChannelDuplexHandler() {
    private class StreamState {
        var child: Channel? = null
        var requestEnded = false
        var inactive = false
        var pendingWrites = 0
    }
    private val streams = mutableMapOf<Http2FrameStream, StreamState>()
    private var inputEnded = false
    private var parentContext: ChannelHandlerContext? = null

    override fun handlerAdded(context: ChannelHandlerContext) { parentContext = context }

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        val frame = when (message) {
            is Http2HeadersFrame -> message.stream().takeIf { message.isEndStream }
            is Http2DataFrame -> message.stream().takeIf { message.isEndStream }
            else -> null
        }
        if (frame != null) streams.getOrPut(frame, ::StreamState).requestEnded = true
        context.fireChannelRead(message)
    }

    /** Registers a stream before its first request frame reaches the HTTP-object adapter. */
    fun attach(channel: Http2StreamChannel) {
        val stream = channel.stream()
        val state = streams.getOrPut(stream, ::StreamState)
        state.child = channel
        channel.pipeline().addLast(PipelineHandlerNames.HTTP2_STREAM_DRAIN, object : ChannelDuplexHandler() {
            override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
                state.pendingWrites++
                val delivery = promise.unvoid()
                delivery.addListener {
                    state.pendingWrites--
                    retire(stream, state)
                }
                context.write(message, delivery)
            }
            override fun channelInactive(context: ChannelHandlerContext) {
                state.inactive = true
                // Let exchange cancellation/completion handlers publish before parent close listeners.
                context.fireChannelInactive()
                retire(stream, state)
            }
        })
        if (inputEnded && !state.requestEnded) channel.close()
    }

    override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
        if (event === ChannelInputShutdownEvent.INSTANCE || event is SslCloseCompletionEvent && event.isSuccess) {
            inputEnded = true
            streams.values.toList().filter { state -> !state.requestEnded }.forEach { state -> state.child?.close() }
            closeIfDrained()
        }
        context.fireUserEventTriggered(event)
    }

    private fun retire(stream: Http2FrameStream, state: StreamState) {
        if (state.inactive && state.pendingWrites == 0) streams.remove(stream)
        closeIfDrained()
    }

    private fun closeIfDrained() {
        val context = parentContext ?: return
        if (inputEnded && streams.isEmpty()) context.executor().execute {
            if (inputEnded && streams.isEmpty()) context.close()
        }
    }
}
