package com.devuloopers.knet.engine.traffic

import com.devuloopers.knet.engine.traffic.processors.MapLocalProcessor
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapLocalProcessorTest {

    @Test
    fun testMapLocalProcessorMatchingAndServing() {
        val tempFile = TestFixtures.createTempFile("""{"mockData":"hello_local"}""")
        val rule = MapLocalRule("ml1", "JSON Mock", ".*api\\.test\\.com.*", tempFile.absolutePath)

        val manager = TrafficModifierManager()
        val channel = EmbeddedChannel(KNetTrafficModifierHandler(manager))
        val request = TestFixtures.createHttpRequest("https://api.test.com/v1/data")

        val handled = MapLocalProcessor.process(channel.pipeline().firstContext(), request, "https://api.test.com/v1/data", listOf(rule))
        assertTrue(handled, "Map Local must match and serve request")

        val response = channel.readOutbound<io.netty.handler.codec.http.FullHttpResponse>()
        assertEquals(200, response.status().code())
        val bodyText = response.content().toString(Charsets.UTF_8)
        assertEquals("""{"mockData":"hello_local"}""", bodyText)
    }

    @Test
    fun testMapLocalNonMatchingUrl() {
        val tempFile = TestFixtures.createTempFile()
        val rule = MapLocalRule("ml1", "JSON Mock", ".*other\\.com.*", tempFile.absolutePath)

        val manager = TrafficModifierManager()
        val channel = EmbeddedChannel(KNetTrafficModifierHandler(manager))
        val request = TestFixtures.createHttpRequest("https://api.test.com/v1/data")

        val handled = MapLocalProcessor.process(channel.pipeline().firstContext(), request, "https://api.test.com/v1/data", listOf(rule))
        assertFalse(handled, "Map Local must not match unrelated URL")
    }
}
