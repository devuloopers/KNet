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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Exactly-once owner of an opaque tunnel's two event loops and finite half-close drain.
 *
 * Quiet fully open tunnels have no idle timer. After one input EOF, the remaining direction gets a
 * fresh idle grace after each successful write. Accepted shaped/backpressured writes suspend that
 * grace. This releases abandoned CLOSE_WAIT/admission slots without truncating active responses.
 */
internal class RawTunnelLifecycle(
    private val capture: ProxyOpaqueFlowCapture?,
    private val halfCloseIdleTimeoutMillis: Long = 60_000L,
) {
    private val channels = CopyOnWriteArraySet<Channel>()
    private val closureMonitor = Any()
    private var terminated = false
    private var closure: OpaqueFlowClosure? = null
    private var inputShutdowns = 0
    private var pendingWrites = 0
    private var completionRequested = false
    private var drainTask: ScheduledFuture<*>? = null
    private var drainGeneration = 0L

    init { require(halfCloseIdleTimeoutMillis > 0L) }

    /** Registers both transport owners before either can publish EOF or terminal failure. */
    fun attach(channel: Channel, peer: Channel) = synchronized(closureMonitor) {
        channels.add(channel)
        channels.add(peer)
        if (terminated) { channel.close(); peer.close() }
    }

    /** Duplicate EOF notifications cannot count as closure of the opposite direction. */
    fun observeInputShutdown(direction: TrafficDirection, occurredAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()) = synchronized(closureMonitor) {
        if (terminated) return@synchronized
        val bit = if (direction == TrafficDirection.CLIENT_TO_SERVER) 1 else 2
        if (inputShutdowns and bit != 0) return@synchronized
        observeClosure(if (direction == TrafficDirection.CLIENT_TO_SERVER) OpaqueFlowCloseInitiator.DEVICE
            else OpaqueFlowCloseInitiator.SERVER, OpaqueFlowCloseReason.PEER_EOF, occurredAtEpochMillis)
        inputShutdowns = inputShutdowns or bit
        if (inputShutdowns == 3) completionRequested = true
        completeOrAwaitDrain()
    }

    /**
     * Records first closure evidence before a propagated/local close. Opposite EOFs within one clock
     * tick remain ambiguous; timeout cleanup retains the peer's original EOF as its initiator.
     */
    fun observeClosure(initiator: OpaqueFlowCloseInitiator, reason: OpaqueFlowCloseReason,
        occurredAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()) = synchronized(closureMonitor) {
        if (terminated) return@synchronized
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

    /** Reserves delivery before handing payload to another event loop; rejects late terminal data. */
    fun writeStarted(): Boolean = synchronized(closureMonitor) {
        if (terminated) return@synchronized false
        pendingWrites++
        cancelDrainTask()
        true
    }

    /** Failed final delivery wins over EOF. Successful progress restarts the half-close idle grace. */
    fun writeFinished(success: Boolean) = synchronized(closureMonitor) {
        check(pendingWrites > 0)
        pendingWrites--
        if (terminated) return@synchronized
        if (!success) {
            observeClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.IO_ERROR)
            terminate(ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED))
        } else completeOrAwaitDrain()
    }

    /** Requests orderly termination after every already-admitted write has settled. */
    fun requestCompletion() = synchronized(closureMonitor) {
        completionRequested = true
        completeOrAwaitDrain()
    }

    private fun completeOrAwaitDrain() {
        if (terminated || pendingWrites != 0) return
        if (completionRequested) terminate(ExchangeTerminalOutcome.Completed)
        else if (inputShutdowns != 0 && drainTask == null) {
            val channel = channels.firstOrNull() ?: return
            val generation = ++drainGeneration
            drainTask = channel.eventLoop().schedule({
                synchronized(closureMonitor) {
                    // Write admission and this callback share the monitor: progress racing expiry
                    // either cancels the old generation or is rejected after terminal ownership.
                    if (!terminated && generation == drainGeneration && pendingWrites == 0 && inputShutdowns != 0) {
                        terminate(ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.TUNNEL_HALF_CLOSE_IDLE_TIMEOUT))
                    }
                }
            }, halfCloseIdleTimeoutMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun cancelDrainTask() {
        drainGeneration++
        drainTask?.cancel(false)
        drainTask = null
    }

    /** Publishes terminal evidence before closing both sockets and releases the scheduled drain task. */
    fun terminate(outcome: ExchangeTerminalOutcome) = synchronized(closureMonitor) {
        if (terminated) return@synchronized
        terminated = true
        cancelDrainTask()
        try {
            capture?.terminate(outcome, Clock.System.now().toEpochMilliseconds())
        } finally {
            channels.forEach { channel -> channel.close() }
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
        if (!lifecycle.writeStarted()) {
            payload.release()
            return
        }
        pendingPeerWrites += 1
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
