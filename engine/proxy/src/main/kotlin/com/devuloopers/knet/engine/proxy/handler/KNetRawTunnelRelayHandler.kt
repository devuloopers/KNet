package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.channel.socket.DuplexChannel
import io.netty.util.ReferenceCountUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock

/** Exactly-once lifecycle shared by both directions of one opaque raw tunnel. */
internal class RawTunnelLifecycle(
    private val capture: ProxyOpaqueFlowCapture?,
) {
    private val terminated = AtomicBoolean(false)
    private val inputShutdowns = AtomicInteger(0)

    /** Records one direction's orderly EOF and reports when both directions have finished. */
    fun observeInputShutdown(): Boolean = inputShutdowns.incrementAndGet() >= 2

    /** Publishes the first terminal outcome and ignores peer-close races. */
    fun terminate(outcome: ExchangeTerminalOutcome) {
        if (!terminated.compareAndSet(false, true)) return
        capture?.terminate(outcome, Clock.System.now().toEpochMilliseconds())
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
) : ChannelInboundHandlerAdapter() {
    private var inputShutdownObserved: Boolean = false
    private var pendingPeerWrites: Int = 0
    private var inactiveObserved: Boolean = false
    private var peerOutputShutdownPending: Boolean = false

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
        peer.writeAndFlush(payload).addListener { writeFuture ->
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
                        } else if (context.channel().isActive && peer.isWritable) {
                            context.read()
                        }
                    }
                }
            }
        }
    }

    override fun channelReadComplete(context: ChannelHandlerContext) {
        if (context.channel().isActive && peer.isWritable) context.read()
        super.channelReadComplete(context)
    }

    override fun channelWritabilityChanged(context: ChannelHandlerContext) {
        if (context.channel().isWritable && peer.isActive) peer.read()
        super.channelWritabilityChanged(context)
    }

    override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
        if (event === ChannelInputShutdownEvent.INSTANCE) {
            if (!inputShutdownObserved) {
                inputShutdownObserved = true
                peerOutputShutdownPending = true
                if (lifecycle.observeInputShutdown()) {
                    lifecycle.terminate(ExchangeTerminalOutcome.Completed)
                }
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
        inactiveObserved = true
        if (pendingPeerWrites == 0) finishInactivePeer()
        super.channelInactive(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        KNetLogger.warn(RAW_TUNNEL_TAG) {
            "Protected tunnel I/O failed direction=$direction cause=${cause::class.simpleName}: ${cause.message}"
        }
        fail(context, TrafficTerminationReason.Transport.DUPLEX_IO_FAILED)
    }

    private fun fail(context: ChannelHandlerContext, reason: TrafficTerminationReason) {
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
        lifecycle.terminate(ExchangeTerminalOutcome.Completed)
        if (peer.isActive) peer.close()
    }

    private companion object {
        const val RAW_TUNNEL_TAG: String = "ProxyEngine"
    }
}
