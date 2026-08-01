package com.devuloopers.knet.engine.traffic

import com.devuloopers.knet.engine.traffic.processors.MapRemoteProcessor
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpHeaderNames
import kotlin.test.Test
import kotlin.test.assertEquals

class MapRemoteProcessorTest {

    @Test
    fun testMapRemoteProcessorHostReRouting() {
        val rule = MapRemoteRule("mr1", "Stage Redirect", ".*prod\\.example\\.com.*", "staging.example.com", 8443)
        val manager = TrafficModifierManager()
        val channel = EmbeddedChannel(KNetTrafficModifierHandler(manager))
        val request = TestFixtures.createHttpRequest("https://prod.example.com/api")

        MapRemoteProcessor.process(channel.pipeline().firstContext(), request, "https://prod.example.com/api", listOf(rule))

        assertEquals("staging.example.com", channel.attr(MapRemoteProcessor.HOST_ATTR).get())
        assertEquals(8443, channel.attr(MapRemoteProcessor.PORT_ATTR).get())
        assertEquals("staging.example.com", request.headers().get(HttpHeaderNames.HOST))
    }
}
