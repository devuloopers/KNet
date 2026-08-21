package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointExtension
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointProtocol
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.HttpHeaderNames
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
    fun `request capture is admitted before the matching breakpoint is published`() {
        val coordinator = coordinator(BreakpointPhase.REQUEST)
        val capture = RecordingConnectionCapture()
        val channel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        channel.attr(ProxyChannelAttributes.CONNECTION_CAPTURE).set(capture)
        val request = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")

        channel.writeInbound(request)
        val pending = awaitPending(coordinator, channel)
        val prepared = channel.attr(ProxyChannelAttributes.PREPARED_EXCHANGE).get()

        assertEquals(listOf(pending.candidate.exchangeId), capture.startedExchangeIds)
        assertEquals(pending.candidate.exchangeId, prepared.exchangeId)
        assertSame(capture.exchange, prepared.capture)

        runBlocking { coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged) }
        awaitCondition(channel) { channel.config().isAutoRead }
        channel.readInbound<FullHttpRequest>().release()
        channel.finishAndReleaseAll()
    }

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
        val dropCapture = RecordingConnectionCapture()
        val dropChannel = EmbeddedChannel(KNetInterceptorHandler(dropCoordinator))
        dropChannel.attr(ProxyChannelAttributes.CONNECTION_CAPTURE).set(dropCapture)
        val dropped = TestFixtures.createFullHttpRequest("https://api.example.com/drop")
        dropChannel.writeInbound(dropped)
        val pending = awaitPending(dropCoordinator, dropChannel)
        runBlocking { dropCoordinator.resolve(pending.id, BreakpointDecision.Drop) }
        awaitCondition(dropChannel) { !dropChannel.isOpen }
        assertEquals(0, dropped.refCnt())
        assertEquals(listOf(ExchangeState.CANCELLED), dropCapture.exchange.terminalStates)
        dropChannel.finishAndReleaseAll()

        val disconnectCoordinator = coordinator(BreakpointPhase.REQUEST)
        val disconnectCapture = RecordingConnectionCapture()
        val disconnectChannel = EmbeddedChannel(KNetInterceptorHandler(disconnectCoordinator))
        disconnectChannel.attr(ProxyChannelAttributes.CONNECTION_CAPTURE).set(disconnectCapture)
        val disconnected = TestFixtures.createFullHttpRequest("https://api.example.com/disconnect")
        disconnectChannel.writeInbound(disconnected)
        awaitPending(disconnectCoordinator, disconnectChannel)
        disconnectChannel.close()
        awaitCondition(disconnectChannel) { disconnectCoordinator.pendingBreakpoints.value.isEmpty() }
        assertEquals(0, disconnected.refCnt())
        assertEquals(listOf(ExchangeState.CANCELLED), disconnectCapture.exchange.terminalStates)
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
    fun `response breakpoint exposes negotiated upstream application protocol`() {
        val coordinator = coordinator(BreakpointPhase.RESPONSE)
        val channel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        val httpTwo = ApplicationProtocol.fromToken("h2")
        channel.attr(ProxyChannelAttributes.UPSTREAM_APPLICATION_PROTOCOL).set(httpTwo)
        channel.writeInbound(TestFixtures.createFullHttpRequest("https://api.example.com/v1/data"))
        awaitCondition(channel) { channel.config().isAutoRead }
        channel.readInbound<FullHttpRequest>().release()

        channel.writeOutbound(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
        val pending = awaitPending(coordinator, channel)

        assertEquals(httpTwo, pending.candidate.response?.head?.protocol)
        runBlocking { coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged) }
        awaitCondition(channel) { channel.config().isAutoRead }
        channel.readOutbound<FullHttpResponse>().release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `provisional response does not consume final response correlation`() {
        val coordinator = coordinator(BreakpointPhase.RESPONSE)
        val channel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        channel.writeInbound(TestFixtures.createFullHttpRequest("https://api.example.com/v1/data"))
        awaitCondition(channel) { channel.config().isAutoRead }
        channel.readInbound<FullHttpRequest>().release()

        val provisional = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)
        channel.writeOutbound(provisional)
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
        assertSame(provisional, channel.readOutbound<FullHttpResponse>())
        provisional.release()

        val finalResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        channel.writeOutbound(finalResponse)
        val pending = awaitPending(coordinator, channel)
        runBlocking { coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged) }
        awaitCondition(channel) { channel.config().isAutoRead }
        channel.readOutbound<FullHttpResponse>().release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `unselected response does not consume later selected request correlation`() {
        val coordinator = BreakpointCoordinator().also { value ->
            value.replaceRules(
                listOf(
                    BreakpointRule(
                        id = "selected-response",
                        phase = BreakpointPhase.RESPONSE,
                        urlPattern = "*selected*",
                    ),
                ),
            )
        }
        val channel = EmbeddedChannel(KNetInterceptorHandler(coordinator))

        val unselectedRequest = DefaultHttpRequest(
            HttpVersion.HTTP_1_1,
            io.netty.handler.codec.http.HttpMethod.GET,
            "https://api.example.com/unselected",
        ).apply { headers().set(HttpHeaderNames.HOST, "api.example.com") }
        channel.writeInbound(unselectedRequest)
        assertSame(unselectedRequest, channel.readInbound<DefaultHttpRequest>())

        val selectedRequest = TestFixtures.createFullHttpRequest("https://api.example.com/selected")
        channel.writeInbound(selectedRequest)
        awaitCondition(channel) { channel.config().isAutoRead }
        channel.readInbound<FullHttpRequest>().release()

        val unselectedResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        channel.writeOutbound(unselectedResponse)
        assertSame(unselectedResponse, channel.readOutbound<DefaultHttpResponse>())

        val selectedResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        channel.writeOutbound(selectedResponse)
        val pending = awaitPending(coordinator, channel)

        assertEquals("https://api.example.com:443/selected", pending.candidate.request.absoluteUrl())
        runBlocking { coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged) }
        awaitCondition(channel) { channel.config().isAutoRead }
        channel.readOutbound<FullHttpResponse>().release()
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

    @Test
    fun `live handler pauses only the configured GraphQL operation`() {
        val extension = GraphQLBreakpointExtension()
        val criteria = requireNotNull(
            extension.createCriteria(
                listOf(
                    ProtocolCriteriaValue(
                        GraphQLBreakpointProtocol.operationNameFieldId,
                        "GetProfile",
                    ),
                ),
            ),
        )
        val coordinator = BreakpointCoordinator(
            protocolRegistry = BreakpointProtocolRegistry(listOf(extension)),
        ).also { value ->
            value.replaceRules(
                listOf(
                    BreakpointRule(
                        id = "graphql-operation",
                        phase = BreakpointPhase.REQUEST,
                        urlPattern = "*graphql*",
                        method = com.devuloopers.knet.traffic.model.http.HttpMethod.POST,
                        protocolCriteria = criteria,
                    ),
                ),
            )
        }

        val otherChannel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        val other = graphQLRequest("UpdateProfile")
        otherChannel.writeInbound(other)
        awaitCondition(otherChannel) { otherChannel.config().isAutoRead }
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
        otherChannel.readInbound<FullHttpRequest>().release()
        otherChannel.finishAndReleaseAll()

        val matchingChannel = EmbeddedChannel(KNetInterceptorHandler(coordinator))
        val matching = graphQLRequest("GetProfile")
        matchingChannel.writeInbound(matching)
        val pending = awaitPending(coordinator, matchingChannel)
        assertEquals("graphql-operation", pending.ruleId)
        runBlocking { coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged) }
        awaitCondition(matchingChannel) { matchingChannel.config().isAutoRead }
        matchingChannel.readInbound<FullHttpRequest>().release()
        matchingChannel.finishAndReleaseAll()
    }

    private fun coordinator(phase: BreakpointPhase): BreakpointCoordinator =
        BreakpointCoordinator().also { coordinator ->
            coordinator.replaceRules(
                listOf(BreakpointRule(id = "rule-1", phase = phase, urlPattern = "*api.example.com*")),
            )
        }

    private fun graphQLRequest(operationName: String): FullHttpRequest = TestFixtures.createFullHttpRequest(
        uri = "https://api.example.com/graphql",
        method = io.netty.handler.codec.http.HttpMethod.POST,
        body = """{"operationName":"$operationName","query":"query $operationName { viewer { id } }"}""",
    ).apply {
        headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
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

    private class RecordingConnectionCapture : ProxyConnectionCapture {
        val startedExchangeIds = mutableListOf<ExchangeId>()
        val exchange = NoOpExchangeCapture()

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: com.devuloopers.knet.traffic.model.TrafficOrigin,
            streamId: com.devuloopers.knet.traffic.id.StreamId?,
        ): ProxyExchangeCapture {
            startedExchangeIds += exchangeId
            exchange.exchangeId = exchangeId
            return exchange
        }

        override fun close(errorCode: String?) = Unit
    }

    private class NoOpExchangeCapture : ProxyExchangeCapture {
        override var exchangeId: ExchangeId = ExchangeId("unassigned")
        val terminalStates = mutableListOf<ExchangeState>()

        override fun tryReserveBody(
            direction: TrafficDirection,
            contentEncoding: ContentEncoding?,
            requestedBytes: Int,
        ): ProxyBodyReservation? = null

        override fun completeBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
        ) = Unit

        override fun cancelBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
            errorCode: String,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

        override fun terminate(
            state: ExchangeState,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
            errorCode: String?,
        ) {
            terminalStates += state
        }
    }
}
