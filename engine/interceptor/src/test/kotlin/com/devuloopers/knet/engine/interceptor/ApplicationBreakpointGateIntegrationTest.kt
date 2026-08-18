package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Netty ownership and lifecycle coverage for the application-owned breakpoint gate. */
class ApplicationBreakpointGateIntegrationTest {
    @Test
    fun `request pause resumes unchanged with exact reference ownership`() {
        val coordinator = coordinator(BreakpointPhase.REQUEST)
        val channel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        val request = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")

        channel.writeInbound(request)
        val pending = awaitPending(coordinator, channel)
        assertFalse(channel.config().isAutoRead)
        runBlocking { assertTrue(coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged)) }
        awaitCondition(channel) { channel.config().isAutoRead }
        val forwarded = channel.readInbound<FullHttpRequest>()

        assertSame(request, forwarded)
        assertEquals(1, forwarded.refCnt())
        forwarded.release()
        assertEquals(0, request.refCnt())
        channel.finishAndReleaseAll()
    }

    @Test
    fun `request drop and disconnect release paused frames once`() {
        val dropCoordinator = coordinator(BreakpointPhase.REQUEST)
        val dropChannel = EmbeddedChannel(KNetInterceptorHandler(dropCoordinator))
        val dropped = TestFixtures.createFullHttpRequest("https://api.example.com/drop")
        dropChannel.writeInbound(dropped)
        val pending = awaitPending(dropCoordinator, dropChannel)
        runBlocking { dropCoordinator.resolve(pending.id, BreakpointDecision.Drop) }
        awaitCondition(dropChannel) { !dropChannel.isOpen }
        assertEquals(0, dropped.refCnt())
        dropChannel.finishAndReleaseAll()

        val disconnectCoordinator = coordinator(BreakpointPhase.REQUEST)
        val disconnectChannel = EmbeddedChannel(KNetInterceptorHandler(disconnectCoordinator))
        val disconnected = TestFixtures.createFullHttpRequest("https://api.example.com/disconnect")
        disconnectChannel.writeInbound(disconnected)
        awaitPending(disconnectCoordinator, disconnectChannel)
        disconnectChannel.close()
        awaitCondition(disconnectChannel) { disconnectCoordinator.pendingBreakpoints.value.isEmpty() }
        assertEquals(0, disconnected.refCnt())
        disconnectChannel.finishAndReleaseAll()
    }

    @Test
    fun `response pause preserves ordering promise and ownership`() {
        val coordinator = coordinator(BreakpointPhase.RESPONSE)
        val channel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        val request = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")
        channel.writeInbound(request)
        awaitCondition(channel) { channel.config().isAutoRead }
        val forwardedRequest = channel.readInbound<FullHttpRequest>()
        forwardedRequest.release()

        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        channel.writeOutbound(response)
        val pending = awaitPending(coordinator, channel)
        runBlocking { coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged) }
        awaitCondition(channel) { channel.config().isAutoRead }
        val forwardedResponse = channel.readOutbound<FullHttpResponse>()

        assertSame(response, forwardedResponse)
        assertEquals(1, forwardedResponse.refCnt())
        forwardedResponse.release()
        assertEquals(0, response.refCnt())
        channel.finishAndReleaseAll()
    }

    @Test
    fun `connect handshake bypasses the breakpoint gate`() {
        val coordinator = coordinator(BreakpointPhase.REQUEST)
        val channel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        val connect = TestFixtures.createFullHttpRequest().apply {
            setMethod(io.netty.handler.codec.http.HttpMethod.CONNECT)
            setUri("api.example.com:443")
        }
        channel.writeInbound(connect)

        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
        assertSame(connect, channel.readInbound<FullHttpRequest>())
        connect.release()
        channel.finishAndReleaseAll()
    }

    private fun coordinator(phase: BreakpointPhase): BreakpointCoordinator =
        BreakpointCoordinator().also { coordinator ->
            coordinator.replaceRules(
                listOf(BreakpointRule(id = "rule-1", phase = phase, urlPattern = "*api.example.com*")),
            )
        }

    private fun awaitPending(
        coordinator: BreakpointCoordinator,
        channel: EmbeddedChannel,
    ): PendingBreakpoint {
        awaitCondition(channel) { coordinator.pendingBreakpoints.value.isNotEmpty() }
        return coordinator.pendingBreakpoints.value.single()
    }

    private fun awaitCondition(channel: EmbeddedChannel, predicate: () -> Boolean) {
        runBlocking {
            repeat(200) {
                channel.runPendingTasks()
                if (predicate()) return@runBlocking
                delay(5)
            }
            error("Timed out waiting for embedded breakpoint pipeline.")
        }
    }
}
