package com.devuloopers.knet.engine.interceptor

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InterceptorHandlerIntegrationTest {

    @BeforeTest
    fun setUp() {
        BreakpointRuleRegistry.clearRules()
        InterceptSessionManager.clearSuspensions()
    }

    @Test
    fun testRequestBreakpointInterceptionAndResume() {
        BreakpointRuleRegistry.addRule(
            BreakpointRule("b1", ".*api\\.example\\.com.*", "GET", BreakpointPhase.REQUEST)
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

        // Resume event with modified request
        val modifiedDto = TestFixtures.createHttpRequestDto(
            url = "https://api.example.com/v1/data",
            headers = listOf("X-Resumed" to "true")
        )
        InterceptSessionManager.resume(event.id, InterceptResult.Resume(modifiedRequest = modifiedDto))

        channel.runPendingTasks()

        val processedReq = channel.readInbound<io.netty.handler.codec.http.FullHttpRequest>()
        assertEquals("true", processedReq.headers().get("X-Resumed"))
    }
}
