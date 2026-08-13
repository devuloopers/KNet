package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.RuleType
import io.netty.channel.embedded.EmbeddedChannel
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
            RuleModel("b1", "b1", RuleType.REQUEST, ".*api\\.example\\.com.*", "GET")
        )

        val handler = KNetInterceptorHandler()
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")
        channel.writeInbound(req)

        // Verify backpressure enabled
        assertFalse(channel.config().isAutoRead, "AutoRead must be disabled when breakpoint hits")

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
}
