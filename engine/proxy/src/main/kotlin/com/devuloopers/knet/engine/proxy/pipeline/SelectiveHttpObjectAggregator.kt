package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpMessage
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.LastHttpContent
import io.netty.util.ReferenceCountUtil

/**
 * Aggregates selected HTTP/1 messages without turning the edit limit into a traffic limit.
 *
 * A selected message is retained only up to [maximumContentBytes]. If the body crosses that limit,
 * the original head and buffered content are replayed in order and the rest of that message passes
 * through incrementally. Unselected messages never enter the buffer. This keeps the proxy usable
 * for large uploads/downloads while still providing bounded full messages to optional inspectors.
 *
 * @param maximumContentBytes Maximum body bytes retained for one selected message.
 * @param shouldAggregate Cheap message-head predicate supplied by an outer capability adapter.
 */
open class SelectiveHttpObjectAggregator(
    private val maximumContentBytes: Int,
    private val shouldAggregate: (ChannelHandlerContext, HttpMessage) -> Boolean,
) : ChannelInboundHandlerAdapter() {
    private var bufferedHead: HttpMessage? = null
    private val bufferedContent = ArrayDeque<HttpContent>()
    private var bufferedBytes: Int = 0
    private var passingCurrentMessage: Boolean = false

    init {
        require(maximumContentBytes > 0) { "Maximum aggregated content must be positive." }
    }

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        when (message) {
            is FullHttpMessage -> acceptFullMessage(context, message)
            is HttpMessage -> acceptHead(context, message)
            is HttpContent -> acceptContent(context, message)
            else -> context.fireChannelRead(message)
        }
    }

    private fun acceptFullMessage(context: ChannelHandlerContext, message: FullHttpMessage) {
        if (!shouldAggregate(context, message) || message.content().readableBytes() <= maximumContentBytes) {
            context.fireChannelRead(message)
            return
        }

        val head = when (message) {
            is HttpRequest -> DefaultHttpRequest(
                message.protocolVersion(),
                message.method(),
                message.uri(),
            )
            is HttpResponse -> DefaultHttpResponse(message.protocolVersion(), message.status())
            else -> {
                ReferenceCountUtil.release(message)
                error("Unsupported full HTTP message type: ${message::class.simpleName}")
            }
        }
        head.headers().set(message.headers())
        head.setDecoderResult(message.decoderResult())
        val last = DefaultLastHttpContent(message.content().retainedDuplicate())
        last.trailingHeaders().set(message.trailingHeaders())
        ReferenceCountUtil.release(message)

        var lastTransferred = false
        try {
            context.fireChannelRead(head)
            // Netty owns the last object once it enters the next handler, including when that
            // handler reports a failure synchronously.
            lastTransferred = true
            context.fireChannelRead(last)
        } finally {
            if (!lastTransferred) ReferenceCountUtil.release(last)
        }
    }

    private fun acceptHead(context: ChannelHandlerContext, message: HttpMessage) {
        check(bufferedHead == null && !passingCurrentMessage) {
            "Received a new HTTP head before the previous message completed."
        }
        if (!shouldAggregate(context, message)) {
            passingCurrentMessage = true
            context.fireChannelRead(message)
            return
        }
        if (message is HttpRequest && HttpUtil.is100ContinueExpected(message)) {
            context.writeAndFlush(DefaultFullHttpResponse(message.protocolVersion(), HttpResponseStatus.CONTINUE))
            HttpUtil.set100ContinueExpected(message, false)
        }
        bufferedHead = message
    }

    private fun acceptContent(context: ChannelHandlerContext, content: HttpContent) {
        if (passingCurrentMessage) {
            if (content is LastHttpContent) passingCurrentMessage = false
            context.fireChannelRead(content)
            return
        }

        val head = bufferedHead
        if (head == null) {
            context.fireChannelRead(content)
            return
        }

        bufferedContent.addLast(content)
        val readableBytes = content.content().readableBytes()
        if (readableBytes > maximumContentBytes - bufferedBytes) {
            replayAsStream(context)
            passingCurrentMessage = content !is LastHttpContent
            return
        }
        bufferedBytes += readableBytes
        if (content is LastHttpContent) emitFullMessage(context, head, content)
    }

    private fun emitFullMessage(
        context: ChannelHandlerContext,
        head: HttpMessage,
        lastContent: LastHttpContent,
    ) {
        val combined = context.alloc().compositeBuffer(maxOf(2, bufferedContent.size))
        var aggregationCompleted = false
        try {
            bufferedContent.forEach { content ->
                if (content.content().isReadable) {
                    combined.addComponent(true, content.content().retain())
                }
            }
            val full = when (head) {
                is HttpRequest -> DefaultFullHttpRequest(
                    head.protocolVersion(),
                    head.method(),
                    head.uri(),
                    combined,
                )
                is HttpResponse -> DefaultFullHttpResponse(
                    head.protocolVersion(),
                    head.status(),
                    combined,
                )
                else -> error("Unsupported HTTP message type: ${head::class.simpleName}")
            }
            full.headers().set(head.headers())
            full.trailingHeaders().set(lastContent.trailingHeaders())
            full.setDecoderResult(
                if (head.decoderResult().isSuccess) lastContent.decoderResult() else head.decoderResult(),
            )
            releaseBufferedObjects()
            bufferedHead = null
            bufferedBytes = 0
            aggregationCompleted = true
            context.fireChannelRead(full)
        } finally {
            if (!aggregationCompleted) {
                combined.release()
                releaseBufferedObjects()
                bufferedHead = null
                bufferedBytes = 0
            }
        }
    }

    /** Transfers the stored objects to the next handler without changing their ownership. */
    private fun replayAsStream(context: ChannelHandlerContext) {
        bufferedHead?.let(context::fireChannelRead)
        bufferedHead = null
        while (bufferedContent.isNotEmpty()) {
            context.fireChannelRead(bufferedContent.removeFirst())
        }
        bufferedBytes = 0
    }

    private fun releaseBufferedObjects() {
        ReferenceCountUtil.release(bufferedHead)
        while (bufferedContent.isNotEmpty()) {
            ReferenceCountUtil.release(bufferedContent.removeFirst())
        }
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        releaseBufferedObjects()
        bufferedHead = null
        bufferedBytes = 0
        super.channelInactive(context)
    }

    override fun handlerRemoved(context: ChannelHandlerContext) {
        releaseBufferedObjects()
        bufferedHead = null
        bufferedBytes = 0
        super.handlerRemoved(context)
    }
}
