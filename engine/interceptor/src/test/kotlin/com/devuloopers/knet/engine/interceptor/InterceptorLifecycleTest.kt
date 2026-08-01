package com.devuloopers.knet.engine.interceptor

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterceptorLifecycleTest {

    @BeforeTest
    fun setUp() {
        BreakpointRuleRegistry.clearRules()
        InterceptSessionManager.clearSuspensions()
    }

    @Test
    fun testDropEventClosesChannelAndCleansUp() {
        BreakpointRuleRegistry.addRule(
            BreakpointRule("b1", ".*api\\.example\\.com.*", "GET", BreakpointPhase.REQUEST)
        )

        val handler = KNetInterceptorHandler()
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")
        channel.writeInbound(req)

        val event = InterceptSessionManager.getActiveEvents().first()
        InterceptSessionManager.resume(event.id, InterceptResult.Drop)

        channel.runPendingTasks()

        assertFalse(channel.isOpen, "Channel must close immediately on InterceptResult.Drop")
        assertTrue(InterceptSessionManager.getActiveEvents().isEmpty())
    }

    @Test
    fun testChannelInactiveCleansUpSuspensions() {
        BreakpointRuleRegistry.addRule(
            BreakpointRule("b1", ".*api\\.example\\.com.*", "GET", BreakpointPhase.REQUEST)
        )

        val handler = KNetInterceptorHandler()
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")
        channel.writeInbound(req)

        channel.close()
        InterceptSessionManager.clearSuspensions()
        assertTrue(InterceptSessionManager.getActiveEvents().isEmpty())
    }
}
