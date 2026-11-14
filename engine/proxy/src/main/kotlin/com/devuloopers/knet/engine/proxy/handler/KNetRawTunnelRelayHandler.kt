package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.traffic.model.OpaqueFlowClosure
import com.devuloopers.knet.traffic.model.OpaqueFlowCloseInitiator
import com.devuloopers.knet.traffic.model.OpaqueFlowCloseReason
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.channel.socket.DuplexChannel
import io.netty.util.ReferenceCountUtil
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock

/** Exactly-once lifecycle shared by both directions of one opaque raw tunnel. */
internal class RawTunnelLifecycle(
    private val capture: ProxyOpaqueFlowCapture?,
) {
    private val channels = CopyOnWriteArraySet<Channel>()
    private val terminated = AtomicBoolean(false)

    /** Registers transport ownership so terminal half-closes release both admitted sockets. */
    fun attach(channel: Channel, peer: Channel) {
        channels.add(channel)
        channels.add(peer)
        if (terminated.get()) { channel.close(); peer.close() }
    }
    private val closureMonitor = Any()
    private var closure: OpaqueFlowClosure? = null
    private val inputShutdowns = AtomicInteger(0)
    private val pendingWrites = AtomicInteger(0)
    private val completionRequested = AtomicBoolean(false)

    /** Records incoming EOF before forwarding a half-close; queued writes still own final delivery. */
    fun observeInputShutdown(direction: TrafficDirection, occurredAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()) {
        observeClosure(if (direction == TrafficDirection.CLIENT_TO_SERVER) OpaqueFlowCloseInitiator.DEVICE
            else OpaqueFlowCloseInitiator.SERVER, OpaqueFlowCloseReason.PEER_EOF, occurredAtEpochMillis)
        if (inputShutdowns.incrementAndGet() >= 2) requestCompletion()
    }

    /**
     * First evidence wins before any propagated/local peer close. Opposite EOFs in one wall-clock tick are
     * conservatively ambiguous. Publish immediately: connection close listeners can end capture before inactive.
     */
    fun observeClosure(initiator: OpaqueFlowCloseInitiator, reason: OpaqueFlowCloseReason,
        occurredAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()) = synchronized(closureMonitor) {
        if (terminated.get()) return@synchronized
        val incoming = OpaqueFlowClosure(initiator, reason, occurredAtEpochMillis)
        val first = closure
        val next = when {
            first == null -> incoming
            first.reason == OpaqueFlowCloseReason.PEER_EOF && reason == OpaqueFlowCloseReason.PEER_EOF &&
                first.initiator != initiator && first.observedAtEpochMillis == occurredAtEpochMillis ->
                OpaqueFlowClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.CONCURRENT_PEER_EOF, occurredAtEpochMillis)
            else -> first
        }
        if (next != first) { closure = next; capture?.observeClosure(next) }
    }

    /** Reserves one shared terminal obligation before handing payload to the peer's outbound pipeline. */
    fun writeStarted() { pendingWrites.incrementAndGet() }

    /** A failed final write must win over success; only successful drained delivery can complete the flow. */
    fun writeFinished(success: Boolean) {
        if (!success) {
            observeClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.IO_ERROR)
            terminate(ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED))
        }
        pendingWrites.decrementAndGet()
        completeIfDrained()
    }

    /** Requests orderly termination after every already-admitted write has completed. */
    fun requestCompletion() {
        completionRequested.set(true)
        completeIfDrained()
    }

    private fun completeIfDrained() {
        if (completionRequested.get() && pendingWrites.get() == 0) terminate(ExchangeTerminalOutcome.Completed)
    }

    /** Publishes the first terminal outcome and ignores peer-close races. */
    fun terminate(outcome: ExchangeTerminalOutcome) {
        synchronized(closureMonitor) {
            if (!terminated.compareAndSet(false, true)) return
            try {
                capture?.terminate(outcome, Clock.System.now().toEpochMilliseconds())
            } finally {
                channels.forEach { channel -> channel.close() }
            }
        }
    }
}

/**
 * Backpressure-aware, payload-opaque relay used for protected CONNECT traffic.
 *
 * Each handler owns one inbound direction. Reads advance only after the peer accepts the previous
 * write, and input half-close is propagated as peer output half-close without terminating the
 * opposite direction prematurely.
 */
internal class KNetRawTunnelRelayHandler(
    private val peer: Channel,
    private val direction: TrafficDirection,
    private val capture: ProxyOpaqueFlowCapture?,
    private val lifecycle: RawTunnelLifecycle,
) : ChannelDuplexHandler() {
    private var inputShutdownObserved: Boolean = false
    private var pendingPeerWrites: Int = 0
    private var inactiveObserved: Boolean = false
    private var peerOutputShutdownPending: Boolean = false

    override fun handlerAdded(context: ChannelHandlerContext) {
        // ClientHello classification reads automatically until route selection. Every raw relay must
        // take ownership of read admission, including the SNI-classified route, before cumulation is replayed.
        lifecycle.attach(context.channel(), peer)
        context.channel().config().isAutoRead = false
    }

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        val payload = message as? ByteBuf
        if (payload == null) {
            ReferenceCountUtil.release(message)
            fail(context, TrafficTerminationReason.Transport.DUPLEX_IO_FAILED)
            return
        }
        if (!peer.isActive) {
            payload.release()
            fail(context, TrafficTerminationReason.Transport.DUPLEX_PEER_CLOSED)
            return
        }
        val byteCount = payload.readableBytes()
        if (byteCount > 0) {
            capture?.observeBytes(direction, byteCount, Clock.System.now().toEpochMilliseconds())
        }
        pendingPeerWrites += 1
        lifecycle.writeStarted()
        peer.writeAndFlush(payload).addListener { writeFuture ->
            // Both peer event loops can finish concurrently; shared terminal accounting is atomic.
            lifecycle.writeFinished(writeFuture.isSuccess)
            context.executor().execute {
                pendingPeerWrites = (pendingPeerWrites - 1).coerceAtLeast(0)
                when {
                    !writeFuture.isSuccess -> fail(
                        context,
                        TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED,
                    )
                    else -> {
                        if (pendingPeerWrites == 0 && peerOutputShutdownPending) {
                            propagatePeerOutputShutdown(context)
                        }
                        if (inactiveObserved && pendingPeerWrites == 0) {
                            finishInactivePeer()
                        } else resumeReads(context)
                    }
                }
            }
        }
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        resumeReads(context)
        super.channelReadComplete(context)
    }

    override fun channelWritabilityChanged(context: ChannelHandlerContext) {
        if (context.channel().isWritable && peer.isActive) {
            // Writability belongs to the destination. Consult the opposite direction's owner on its
            // event loop instead of bypassing its outstanding-write fence with a raw peer.read().
            peer.eventLoop().execute {
                val relay = peer.pipeline().context(KNetRawTunnelRelayHandler::class.java)
                if (relay != null) (relay.handler() as KNetRawTunnelRelayHandler).resumeReads(relay)
            }
        }
        super.channelWritabilityChanged(context)
    }

    private fun resumeReads(context: ChannelHandlerContext) {
        if (pendingPeerWrites == 0 && !inputShutdownObserved && !inactiveObserved &&
            context.channel().isActive && peer.isWritable
        ) context.read()
    }

    override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
        if (event === ChannelInputShutdownEvent.INSTANCE) {
            if (!inputShutdownObserved) {
                inputShutdownObserved = true
                peerOutputShutdownPending = true
                lifecycle.observeInputShutdown(direction)
                if (pendingPeerWrites == 0) {
                    propagatePeerOutputShutdown(context)
                }
            }
            return
        }
        super.userEventTriggered(context, event)
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        KNetLogger.debug(RAW_TUNNEL_TAG) {
            "Protected tunnel channel became inactive direction=$direction " +
                "local=${context.channel().localAddress()} remote=${context.channel().remoteAddress()}"
        }
        lifecycle.observeClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.UNKNOWN)
        inactiveObserved = true
        if (pendingPeerWrites == 0) finishInactivePeer()
        super.channelInactive(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        KNetLogger.warn(RAW_TUNNEL_TAG) {
            "Protected tunnel I/O failed direction=$direction cause=${cause::class.simpleName}: ${cause.message}"
        }
        when (cause) {
            is ReadTimeoutException -> lifecycle.observeClosure(OpaqueFlowCloseInitiator.KNET, OpaqueFlowCloseReason.INACTIVITY_TIMEOUT)
            is WriteTimeoutException -> lifecycle.observeClosure(OpaqueFlowCloseInitiator.KNET, OpaqueFlowCloseReason.WRITE_TIMEOUT)
            else -> lifecycle.observeClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.IO_ERROR)
        }
        fail(context, when (cause) {
            is ReadTimeoutException -> TrafficTerminationReason.Transport.READ_TIMED_OUT
            is WriteTimeoutException -> TrafficTerminationReason.Transport.WRITE_TIMED_OUT
            else -> TrafficTerminationReason.Transport.DUPLEX_IO_FAILED
        })
    }

    /** Observes explicit outbound close before the socket's close future can end capture. */
    override fun close(context: ChannelHandlerContext, promise: ChannelPromise) {
        recordLocalClose(OpaqueFlowCloseReason.LOCAL_CLOSE)
        super.close(context, promise)
    }

    /** Called on the channel event loop before runtime shutdown/reset closes the admitted socket. */
    internal fun recordLocalClose(reason: OpaqueFlowCloseReason) {
        lifecycle.observeClosure(OpaqueFlowCloseInitiator.KNET, reason)
    }

    private fun fail(context: ChannelHandlerContext, reason: TrafficTerminationReason) {
        lifecycle.observeClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.IO_ERROR)
        lifecycle.terminate(ExchangeTerminalOutcome.Failed(reason))
        if (peer.isActive) peer.close()
        context.close()
    }

    /** A FIN must follow queued shaped bytes; sending it first would truncate the tunnel payload. */
    private fun propagatePeerOutputShutdown(context: ChannelHandlerContext) {
        peerOutputShutdownPending = false
        val duplexPeer = peer as? DuplexChannel ?: return
        if (!peer.isActive || duplexPeer.isOutputShutdown) return
        duplexPeer.shutdownOutput().addListener { shutdownFuture ->
            if (!shutdownFuture.isSuccess && context.channel().isActive) {
                context.executor().execute {
                    fail(context, TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED)
                }
            }
        }
    }

    /** Keeps the peer alive until every already-accepted shaped write reaches its terminal promise. */
    private fun finishInactivePeer() {
        lifecycle.requestCompletion()
        if (peer.isActive) peer.close()
    }

    private companion object {
        const val RAW_TUNNEL_TAG: String = "ProxyEngine"
    }
}
