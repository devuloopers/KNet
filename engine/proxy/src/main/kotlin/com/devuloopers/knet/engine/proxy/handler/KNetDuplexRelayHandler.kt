package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.inspection.NettyPayloadSlice
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer
import com.devuloopers.knet.engine.proxy.pipeline.ProxyTransportActivity
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.channel.socket.DuplexChannel
import io.netty.handler.ssl.SslCloseCompletionEvent
import io.netty.handler.ssl.SslHandler
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Clock

/**
 * Shared terminal owner for an upgraded connection's two independent event loops.
 *
 * Both input EOFs request completion; accepted writes and transforms must drain first. One monitor
 * fences admission and terminal selection so a late failed write cannot be hidden by clean EOF.
 * Publication precedes channel close, preserving canonical capture against close-listener races.
 */
internal class DuplexRelayLifecycle(
    private val inspectors: List<ProxyDuplexInspector>,
    private val transformer: ProxyDuplexTransformer?,
    private val onTerminated: (ExchangeTerminalOutcome, Long) -> Unit,
) {
    private val monitor = Any()
    private val channels = CopyOnWriteArraySet<Channel>()
    private var terminated = false
    private var endedDirections = 0
    private var pendingWrites = 0
    private var pendingTransforms = 0
    private var completionRequested = false

    /** Registers both sockets before either can publish EOF or a failure. */
    fun attach(channel: Channel, peer: Channel) {
        channels.add(channel)
        channels.add(peer)
        if (isTerminated()) { channel.close(); peer.close() }
    }

    /** True after one terminal outcome has reserved ownership. */
    fun isTerminated(): Boolean = synchronized(monitor) { terminated }

    /** Admits one write before handing bytes or a TLS close alert to the peer. */
    fun writeStarted(): Boolean = synchronized(monitor) {
        if (terminated) false else { pendingWrites++; true }
    }

    /** Failed delivery wins until every accepted write has settled. */
    fun writeFinished(success: Boolean) {
        val outcome = synchronized(monitor) {
            check(pendingWrites > 0)
            pendingWrites--
            if (!success) reserve(ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED))
            else completeIfDrained()
        }
        publish(outcome)
    }

    /** Holds graceful EOF while an asynchronous protocol decision owns bytes. */
    fun transformStarted(): Boolean = synchronized(monitor) {
        if (terminated) false else { pendingTransforms++; true }
    }

    /** Transfers a transform obligation to its write before this method releases it. */
    fun transformFinished() {
        val outcome = synchronized(monitor) {
            check(pendingTransforms > 0)
            pendingTransforms--
            completeIfDrained()
        }
        publish(outcome)
    }

    /** Duplicate SSL/TCP input notifications for one direction cannot substitute for opposite EOF. */
    fun inputShutdown(direction: TrafficDirection) {
        val outcome = synchronized(monitor) {
            endedDirections = endedDirections or if (direction == TrafficDirection.CLIENT_TO_SERVER) 1 else 2
            if (endedDirections == 3) completionRequested = true
            completeIfDrained()
        }
        publish(outcome)
    }

    /** A fully closed socket cannot retain paused transforms destined for an unusable connection. */
    fun channelClosed() {
        val outcome = synchronized(monitor) {
            completionRequested = true
            if (pendingTransforms > 0) reserve(ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DUPLEX_PEER_CLOSED))
            else completeIfDrained()
        }
        publish(outcome)
    }

    /** Reserves failure/cancellation once, including exceptions racing with queued delivery. */
    fun terminate(outcome: ExchangeTerminalOutcome) {
        publish(synchronized(monitor) { reserve(outcome) })
    }

    private fun completeIfDrained(): ExchangeTerminalOutcome? =
        if (completionRequested && pendingWrites == 0 && pendingTransforms == 0) reserve(ExchangeTerminalOutcome.Completed) else null

    private fun reserve(outcome: ExchangeTerminalOutcome): ExchangeTerminalOutcome? {
        if (terminated) return null
        terminated = true
        return outcome
    }

    private fun publish(outcome: ExchangeTerminalOutcome?) {
        if (outcome == null) return
        val occurredAt = Clock.System.now().toEpochMilliseconds()
        try {
            runCatching { transformer?.cancel(outcome.reason) }
            inspectors.forEach { inspector -> runCatching { inspector.onTerminated(outcome, occurredAt) } }
            onTerminated(outcome, occurredAt)
        } finally {
            channels.forEach { channel -> channel.close() }
        }
    }
}

/**
 * Backpressured upgraded-byte relay. Input half-close follows accepted data/transform writes,
 * preserves the reverse direction, and uses TLS close_notify when that direction has an SSL codec.
 */
internal class KNetDuplexRelayHandler(
    private val peer: Channel,
    private val direction: TrafficDirection,
    private val inspectors: List<ProxyDuplexInspector>,
    private val transformer: ProxyDuplexTransformer?,
    private val lifecycle: DuplexRelayLifecycle,
) : SimpleChannelInboundHandler<ByteBuf>() {
    private var transformInProgress = false
    private var transformHold: AutoCloseable? = null
    private var pendingWrites = 0
    private var inputEnded = false
    private var outputShutdownStarted = false

    override fun handlerAdded(context: ChannelHandlerContext) {
        ProxyTransportActivity.link(context.channel(), peer)
        lifecycle.attach(context.channel(), peer)
        context.channel().config().isAutoRead = false
        super.handlerAdded(context)
    }

    override fun channelRead0(context: ChannelHandlerContext, message: ByteBuf) {
        if (lifecycle.isTerminated()) return
        val occurredAt = Clock.System.now().toEpochMilliseconds()
        val selectedTransformer = transformer
        if (selectedTransformer == null) {
            observe(message, occurredAt)
            writeAndAdvance(context, message.retainedDuplicate())
            return
        }
        check(!transformInProgress) { "Duplex transformer calls must remain serialized per direction." }
        if (!lifecycle.transformStarted()) return
        transformInProgress = true
        val hold = ProxyTransportActivity.hold(context.channel())
        transformHold = hold
        val ownedPayload = ByteArray(message.readableBytes())
        message.getBytes(message.readerIndex(), ownedPayload)
        val resultStage = try { selectedTransformer.transform(direction, ownedPayload, occurredAt) }
        catch (failure: Throwable) {
            hold.close(); transformHold = null; transformInProgress = false
            lifecycle.terminate(ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Interception.DUPLEX_TRANSFORM_FAILED))
            lifecycle.transformFinished()
            return
        }
        resultStage.whenComplete { result, failure ->
            context.executor().execute {
                hold.close()
                if (transformHold === hold) transformHold = null
                transformInProgress = false
                if (lifecycle.isTerminated() || !context.channel().isActive) {
                    lifecycle.transformFinished()
                    return@execute
                }
                if (failure != null || result is ProxyDuplexTransformResult.DropConnection) {
                    val reason = (result as? ProxyDuplexTransformResult.DropConnection)?.reason
                        ?: TrafficTerminationReason.Interception.DUPLEX_TRANSFORM_FAILED
                    lifecycle.terminate(ExchangeTerminalOutcome.Failed(reason))
                } else {
                    val forwarded = (result as ProxyDuplexTransformResult.Forward).copyPayload()
                    if (forwarded.isNotEmpty()) {
                        val output = Unpooled.wrappedBuffer(forwarded)
                        observe(output, occurredAt)
                        // Reserve delivery before releasing the transform: no zero-obligation gap at EOF.
                        writeAndAdvance(context, output)
                    }
                    propagateOutputShutdown(context)
                }
                lifecycle.transformFinished()
                resumeReads(context)
            }
        }
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        resumeReads(context)
        super.channelReadComplete(context)
    }

    override fun channelWritabilityChanged(context: ChannelHandlerContext) {
        if (context.channel().isWritable && peer.isActive) peer.eventLoop().execute {
            val opposite = peer.pipeline().context(KNetDuplexRelayHandler::class.java)
            if (opposite != null) (opposite.handler() as KNetDuplexRelayHandler).resumeReads(opposite)
        }
        super.channelWritabilityChanged(context)
    }

    override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
        if (event === ChannelInputShutdownEvent.INSTANCE || event is SslCloseCompletionEvent && event.isSuccess) {
            if (!inputEnded) {
                inputEnded = true
                propagateOutputShutdown(context)
                lifecycle.inputShutdown(direction)
            }
            return
        }
        super.userEventTriggered(context, event)
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        transformHold?.close()
        transformHold = null
        lifecycle.channelClosed()
        super.channelInactive(context)
    }

    override fun handlerRemoved(context: ChannelHandlerContext) {
        transformHold?.close()
        transformHold = null
        if (context.channel().isActive) lifecycle.terminate(
            ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DUPLEX_PEER_CLOSED),
        )
        super.handlerRemoved(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        lifecycle.terminate(ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_IO_FAILED))
    }

    private fun observe(payload: ByteBuf, occurredAtEpochMillis: Long) {
        if (!payload.isReadable) return
        val slice = NettyPayloadSlice(payload)
        inspectors.forEach { inspector -> runCatching { inspector.onPayload(direction, slice, occurredAtEpochMillis) } }
    }

    private fun resumeReads(context: ChannelHandlerContext) {
        if (!lifecycle.isTerminated() && !inputEnded && !transformInProgress && pendingWrites == 0 &&
            context.channel().isActive && peer.isWritable
        ) context.read()
    }

    private fun writeAndAdvance(context: ChannelHandlerContext, payload: ByteBuf) {
        if (!peer.isActive || !lifecycle.writeStarted()) {
            payload.release()
            lifecycle.terminate(ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DUPLEX_PEER_CLOSED))
            return
        }
        pendingWrites++
        peer.writeAndFlush(payload).addListener { write -> context.executor().execute {
            pendingWrites--
            if (write.isSuccess) propagateOutputShutdown(context)
            lifecycle.writeFinished(write.isSuccess)
            resumeReads(context)
        } }
    }

    /** FIN/close_notify is an accepted write obligation too; it cannot overtake shaped final bytes. */
    private fun propagateOutputShutdown(context: ChannelHandlerContext) {
        if (!inputEnded || outputShutdownStarted || transformInProgress || pendingWrites > 0 || lifecycle.isTerminated()) return
        outputShutdownStarted = true
        if (!lifecycle.writeStarted()) return
        val ssl = peer.pipeline().get(SslHandler::class.java)
        val shutdown = when {
            !peer.isActive -> peer.newSucceededFuture()
            ssl != null -> ssl.closeOutbound()
            peer is DuplexChannel && !peer.isOutputShutdown -> peer.shutdownOutput()
            else -> peer.newSucceededFuture()
        }
        shutdown.addListener { result -> lifecycle.writeFinished(result.isSuccess) }
    }
}
