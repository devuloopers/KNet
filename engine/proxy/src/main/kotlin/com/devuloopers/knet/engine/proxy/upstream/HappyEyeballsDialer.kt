package com.devuloopers.knet.engine.proxy.upstream

import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoop
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.ScheduledFuture
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** The connected socket plus the exact DNS candidate that won the dual-stack race. */
internal data class ConnectedUpstream(
    val channel: Channel,
    val candidate: UpstreamAddressCandidate,
)

/** One bounded candidate failure retained for actionable diagnostics. */
internal data class UpstreamCandidateFailure(
    val family: UpstreamAddressFamily,
    val address: String,
    val detail: String,
)

/** Indicates that every bounded IPv6/IPv4 candidate failed before the connection deadline. */
internal class UpstreamConnectException(
    val failures: List<UpstreamCandidateFailure>,
    message: String,
) : IOException(message)

/**
 * Event-loop-confined RFC 8305-style connector.
 *
 * The platform-preferred address starts immediately. Each alternate candidate starts after a
 * short stagger, or immediately when the preceding attempt fails. The first successful socket
 * owns the result; every losing channel is closed before it can enter an HTTP/TLS pipeline.
 */
internal class HappyEyeballsDialer(
    private val runtimePolicy: KNetProxyRuntimePolicy,
) {

    fun connect(eventLoop: EventLoop, route: ResolvedUpstreamRoute): CompletableFuture<ConnectedUpstream> {
        val result = CompletableFuture<ConnectedUpstream>()
        eventLoop.execute { ConnectionRace(eventLoop, route, result).start() }
        return result
    }

    private inner class ConnectionRace(
        private val eventLoop: EventLoop,
        private val route: ResolvedUpstreamRoute,
        private val result: CompletableFuture<ConnectedUpstream>,
    ) {
        private val activeAttempts = linkedMapOf<ChannelFuture, UpstreamAddressCandidate>()
        private val failures = mutableListOf<UpstreamCandidateFailure>()
        private var nextCandidateIndex: Int = 0
        private var staggeredAttempt: ScheduledFuture<*>? = null
        private var deadline: ScheduledFuture<*>? = null

        fun start() {
            if (result.isDone) return
            deadline = eventLoop.schedule(
                ::onDeadline,
                runtimePolicy.connectTimeoutMillis,
                TimeUnit.MILLISECONDS,
            )
            result.whenComplete { _, _ ->
                if (result.isCancelled) eventLoop.execute(::cancelRace)
            }
            startNextCandidate()
        }

        private fun onDeadline() {
            activeAttempts.values.forEach { candidate ->
                failures += UpstreamCandidateFailure(
                    family = candidate.family,
                    address = candidate.socketAddress.address.hostAddress,
                    detail = "connection attempt timed out",
                )
            }
            failRace(
                "Connection deadline exceeded for ${route.host}:${route.port}: ${failureSummary()}.",
            )
        }

        private fun startNextCandidate() {
            if (result.isDone || nextCandidateIndex >= route.candidates.size) {
                completeIfExhausted()
                return
            }
            staggeredAttempt?.cancel(false)
            staggeredAttempt = null
            val candidate = route.candidates[nextCandidateIndex++]
            val connectFuture = newBootstrap(eventLoop).connect(candidate.socketAddress)
            activeAttempts[connectFuture] = candidate
            connectFuture.addListener { completed ->
                val future = completed as ChannelFuture
                eventLoop.execute { onAttemptCompleted(future, candidate) }
            }
            scheduleNextCandidate()
        }

        private fun scheduleNextCandidate() {
            if (result.isDone || nextCandidateIndex >= route.candidates.size || staggeredAttempt != null) return
            staggeredAttempt = eventLoop.schedule(
                {
                    staggeredAttempt = null
                    startNextCandidate()
                },
                runtimePolicy.happyEyeballsDelayMillis,
                TimeUnit.MILLISECONDS,
            )
        }

        private fun onAttemptCompleted(future: ChannelFuture, candidate: UpstreamAddressCandidate) {
            activeAttempts.remove(future)
            if (result.isDone) {
                future.channel().close()
                return
            }
            if (future.isSuccess) {
                if (result.complete(ConnectedUpstream(future.channel(), candidate))) {
                    staggeredAttempt?.cancel(false)
                    deadline?.cancel(false)
                    activeAttempts.keys.forEach { losingFuture -> losingFuture.channel().close() }
                    activeAttempts.clear()
                } else {
                    future.channel().close()
                }
                return
            }

            future.channel().close()
            failures += UpstreamCandidateFailure(
                family = candidate.family,
                address = candidate.socketAddress.address.hostAddress,
                detail = future.cause().safeDiagnostic(),
            )
            if (nextCandidateIndex < route.candidates.size) {
                staggeredAttempt?.cancel(false)
                staggeredAttempt = null
                startNextCandidate()
            } else {
                completeIfExhausted()
            }
        }

        private fun completeIfExhausted() {
            if (!result.isDone && nextCandidateIndex >= route.candidates.size && activeAttempts.isEmpty()) {
                failRace("All addresses failed for ${route.host}:${route.port}: ${failureSummary()}.")
            }
        }

        private fun failRace(message: String) {
            if (result.completeExceptionally(UpstreamConnectException(failures.toList(), message))) {
                cancelRace()
            }
        }

        private fun cancelRace() {
            staggeredAttempt?.cancel(false)
            deadline?.cancel(false)
            activeAttempts.keys.forEach { future -> future.channel().close() }
            activeAttempts.clear()
        }

        private fun failureSummary(): String = failures
            .take(MAX_DIAGNOSTIC_FAILURES)
            .joinToString(separator = "; ") { failure ->
                "${failure.family.name} ${failure.address}: ${failure.detail}"
            }
            .ifBlank { "no connection attempt completed" }
    }

    private fun newBootstrap(eventLoop: EventLoop): Bootstrap = Bootstrap()
        .group(eventLoop)
        .channel(NioSocketChannel::class.java)
        .option(ChannelOption.AUTO_READ, false)
        .option(ChannelOption.ALLOW_HALF_CLOSURE, true)
        .option(
            ChannelOption.CONNECT_TIMEOUT_MILLIS,
            runtimePolicy.connectTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        .handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(channel: SocketChannel) = Unit
        })

    private fun Throwable?.safeDiagnostic(): String = this
        ?.message
        ?.lineSequence()
        ?.firstOrNull()
        ?.take(MAX_DIAGNOSTIC_DETAIL_LENGTH)
        ?.takeIf(String::isNotBlank)
        ?: this?.javaClass?.simpleName
        ?: "connection failed"

    private companion object {
        const val MAX_DIAGNOSTIC_FAILURES: Int = 4
        const val MAX_DIAGNOSTIC_DETAIL_LENGTH: Int = 160
    }
}
