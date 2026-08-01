package com.devuloopers.knet.engine.traffic

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficHandlerIntegrationTest {

    @Test
    fun testEmbeddedChannelHandlerPipelineExecution() {
        val manager = TrafficModifierManager()
        val rule = ModifierRule("r1", "Add Token", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, "X-Token", "12345")
        manager.addModifierRule(rule)

        val handler = KNetTrafficModifierHandler(manager)
        val channel = EmbeddedChannel(handler)

        val request = TestFixtures.createHttpRequest("https://api.example.com/users")
        channel.writeInbound(request)

        val processedRequest = channel.readInbound<io.netty.handler.codec.http.FullHttpRequest>()
        assertEquals("12345", processedRequest.headers().get("X-Token"))
    }
}
