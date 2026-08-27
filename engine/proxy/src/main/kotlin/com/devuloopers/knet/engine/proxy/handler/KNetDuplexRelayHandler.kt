package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.inspection.NettyPayloadSlice
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock

/**
 * Shared terminal owner for both raw relay directions of one upgraded connection.
 *
 * The first close or failure releases protocol state and closes the peer, making socket shutdown,
 * breakpoint cancellation, and canonical exchange termination one idempotent transition.
 */
internal class DuplexRelayLifecycle(
    private val inspectors: List<ProxyDuplexInspector>,
    private val transformer: ProxyDuplexTransformer?,
    private val onTerminated: (ExchangeTerminalOutcome, Long) -> Unit,
) {
    private val terminated = AtomicBoolean(false)

    /** Terminates both protocol extensions and canonical exchange ownership exactly once. */
    fun terminate(outcome: ExchangeTerminalOutcome) {
        if (!terminated.compareAndSet(false, true)) return
        val occurredAt = Clock.System.now().toEpochMilliseconds()
        transformer?.cancel(outcome.reason)
        inspectors.forEach { inspector ->
            runCatching { inspector.onTerminated(outcome, occurredAt) }
        }
        onTerminated(outcome, occurredAt)
    }
}

/**
 * Backpressured raw-byte relay installed only after an HTTP `101 Switching Protocols` response.
 *
 * Observation stays zero-copy. A heap copy is created only when a selected protocol breakpoint
 * transformer has explicitly claimed the connection.
 */
internal class KNetDuplexRelayHandler(
    private val peer: Channel,
    private val direction: TrafficDirection,
    private val inspectors: List<ProxyDuplexInspector>,
    private val transformer: ProxyDuplexTransformer?,
    private val lifecycle: DuplexRelayLifecycle,
) : SimpleChannelInboundHandler<ByteBuf>() {
    private var transformInProgress = false

    override fun channelRead0(context: ChannelHandlerContext, message: ByteBuf) {
        val occurredAt = Clock.System.now().toEpochMilliseconds()
        val selectedTransformer = transformer
        if (selectedTransformer == null) {
            observe(message, occurredAt)
            writeAndAdvance(context, message.retainedDuplicate())
            return
        }

        check(!transformInProgress) { "Duplex transformer calls must remain serialized per direction." }
        transformInProgress = true
        val ownedPayload = ByteArray(message.readableBytes())
        message.getBytes(message.readerIndex(), ownedPayload)
        selectedTransformer.transform(direction, ownedPayload, occurredAt).whenComplete { result, failure ->
            context.executor().execute {
                transformInProgress = false
                if (failure != null || result is ProxyDuplexTransformResult.DropConnection) {
                    val reason = (result as? ProxyDuplexTransformResult.DropConnection)?.reason
                        ?: TrafficTerminationReason.Interception.DUPLEX_TRANSFORM_FAILED
                    lifecycle.terminate(ExchangeTerminalOutcome.Failed(reason))
                    peer.close()
                    context.close()
                    return@execute
                }
                val forwarded = (result as ProxyDuplexTransformResult.Forward).copyPayload()
                if (forwarded.isEmpty()) {
                    if (context.channel().isActive) context.read()
                    return@execute
                }
                val output = Unpooled.wrappedBuffer(forwarded)
                observe(output, occurredAt)
                writeAndAdvance(context, output)
            }
        }
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        if (!transformInProgress && context.channel().isActive && peer.isWritable) context.read()
        super.channelReadComplete(context)
    }

    override fun channelWritabilityChanged(context: ChannelHandlerContext) {
        if (context.channel().isWritable && peer.isActive) peer.read()
        super.channelWritabilityChanged(context)
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        lifecycle.terminate(ExchangeTerminalOutcome.Completed)
        if (peer.isActive) peer.close()
        super.channelInactive(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        lifecycle.terminate(
            ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_IO_FAILED),
        )
        if (peer.isActive) peer.close()
        context.close()
    }

    private fun observe(payload: ByteBuf, occurredAtEpochMillis: Long) {
        if (!payload.isReadable) return
        val slice = NettyPayloadSlice(payload)
        inspectors.forEach { inspector ->
            runCatching { inspector.onPayload(direction, slice, occurredAtEpochMillis) }
        }
    }

    private fun writeAndAdvance(context: ChannelHandlerContext, payload: ByteBuf) {
        if (!peer.isActive) {
            payload.release()
            lifecycle.terminate(
                ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DUPLEX_PEER_CLOSED),
            )
            context.close()
            return
        }
        peer.writeAndFlush(payload).addListener { write ->
            context.executor().execute {
                if (write.isSuccess && context.channel().isActive && peer.isWritable) {
                    context.read()
                } else if (!write.isSuccess) {
                    lifecycle.terminate(
                        ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED),
                    )
                    peer.close()
                    context.close()
                }
            }
        }
    }

}
