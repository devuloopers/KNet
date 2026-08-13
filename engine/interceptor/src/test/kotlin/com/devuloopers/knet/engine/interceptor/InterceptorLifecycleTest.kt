package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.RuleType
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
            RuleModel("b1", "b1", RuleType.REQUEST, ".*api\\.example\\.com.*", "GET")
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
            RuleModel("b1", "b1", RuleType.REQUEST, ".*api\\.example\\.com.*", "GET")
        )

        val handler = KNetInterceptorHandler()
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest("https://api.example.com/v1/data")
        channel.writeInbound(req)

        channel.close()
        channel.runPendingTasks()

        assertTrue(InterceptSessionManager.getActiveEvents().isEmpty())
    }
}
