package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.domain.rules.model.RuleModel
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterceptorHandlerIntegrationTest {

    @BeforeTest
    fun setUp() {
        BreakpointRuleRegistry.clearRules()
        InterceptSessionManager.clearSuspensions()
    }

    @Test
    fun testRequestBreakpointInterceptionAndResume() {
        BreakpointRuleRegistry.addRule(
            RuleModel("b1", "b1", BreakpointPhase.REQUEST, ".*api\\.example\\.com.*", "GET")
        )

        var capturedImmediately: HttpRequest? = null
        val listener = object : ProxyTrafficListener {
            override fun onRequestCaptured(request: HttpRequest) {
                capturedImmediately = request
            }
        }

        val handler = KNetInterceptorHandler(listener)
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")
        channel.writeInbound(req)

        // Verify backpressure enabled
        assertFalse(channel.config().isAutoRead, "AutoRead must be disabled when breakpoint hits")

        // Verify immediate listener notification
        val captured = capturedImmediately
        assertTrue(captured != null, "Request must be captured immediately upon breakpoint hit")
        assertTrue(captured.isIntercepted, "Captured request must have isIntercepted = true")
        assertEquals("b1", captured.matchedRuleId)

        val activeEvents = InterceptSessionManager.getActiveEvents()
        assertEquals(1, activeEvents.size)
        val event = activeEvents.first()

        // Verify event-driven interception tagging
        assertTrue(event.request.isIntercepted, "Intercepted event request must have isIntercepted = true")
        assertEquals("b1", event.request.matchedRuleId, "Intercepted event request must match rule ID b1")

        // Resume event with modified request
        val modifiedDto = TestFixtures.createHttpRequestDto(
            url = "https://api.example.com/v1/data",
            headers = listOf("X-Resumed" to "true")
        )
        val resumed = InterceptSessionManager.resume(event.id, InterceptResult.Resume(modifiedRequest = modifiedDto))
        assertEquals(true, resumed)
    }

    @Test
    fun testConnectHandshakeBypassedWithoutInterception() {
        BreakpointRuleRegistry.addRule(
            RuleModel("b2", "b2", BreakpointPhase.REQUEST, ".*stg-04astra\\.cnbc\\.com.*", "ALL")
        )

        val handler = KNetInterceptorHandler()
        val channel = EmbeddedChannel(handler)

        val connectReq = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.CONNECT,
            "stg-04astra.cnbc.com:443"
        )
        channel.writeInbound(connectReq)

        // Verify CONNECT request was NOT suspended
        assertTrue(InterceptSessionManager.getActiveEvents().isEmpty(), "CONNECT handshake must not trigger breakpoint suspension")
        assertTrue(channel.config().isAutoRead, "AutoRead must remain enabled after CONNECT pass-through")
    }

    @Test
    fun testHttpsDecryptedRequestWithGraphQLBodyInterception() {
        BreakpointRuleRegistry.addRule(
            RuleModel(
                id = "gql-rule-1",
                name = "FormattedQuotes GraphQL Rule",
                type = BreakpointPhase.REQUEST,
                condition = ".*stg-04astra\\.cnbc\\.com.*",
                action = "POST",
                protocolCriteria = ProtocolMatchCriteria.GraphQL(operationName = "FormattedQuotes")
            )
        )

        val handler = KNetInterceptorHandler()
        val channel = EmbeddedChannel(handler)

        // Simulate SSL host attribute set on channel
        channel.attr(ChannelAttributes.HOST_ATTR).set("stg-04astra.cnbc.com")
        channel.attr(ChannelAttributes.SSL_ATTR).set(true)

        val gqlBody = "{\"query\":\"query FormattedQuotes { quotes { symbol price } }\",\"operationName\":\"FormattedQuotes\"}"
        val req = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/graphql",
            Unpooled.copiedBuffer(gqlBody, Charsets.UTF_8)
        )
        req.headers().set(HttpHeaderNames.HOST, "stg-04astra.cnbc.com")
        req.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")

        channel.writeInbound(req)

        // Verify request was intercepted
        assertFalse(channel.config().isAutoRead, "AutoRead must be disabled when GraphQL breakpoint hits")
        val activeEvents = InterceptSessionManager.getActiveEvents()
        assertEquals(1, activeEvents.size, "Exactly one in-flight interception event must be active")

        val event = activeEvents.first()
        assertEquals("gql-rule-1", event.request.matchedRuleId)
        assertTrue(event.request.isIntercepted)
        assertEquals("https://stg-04astra.cnbc.com/graphql", event.request.url)
        assertEquals("POST", event.request.method)
    }

    @Test
    fun testRequestBreakpointDropNotifiesListener() {
        BreakpointRuleRegistry.addRule(
            RuleModel("b3", "b3", BreakpointPhase.REQUEST, ".*drop\\.example\\.com.*", "GET")
        )

        var droppedTxId: String? = null
        var dropReason: String? = null
        val listener = object : ProxyTrafficListener {
            override fun onTransactionDropped(transactionId: String, reason: String) {
                droppedTxId = transactionId
                dropReason = reason
            }
        }

        val handler = KNetInterceptorHandler(listener)
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest("https://drop.example.com/test")
        channel.writeInbound(req)

        val activeEvents = InterceptSessionManager.getActiveEvents()
        assertEquals(1, activeEvents.size)
        val event = activeEvents.first()

        // Drop the transaction
        InterceptSessionManager.resume(event.id, InterceptResult.Drop)
        channel.runPendingTasks()

        assertEquals(event.request.id, droppedTxId)
        assertEquals("Dropped", dropReason)
    }
}
