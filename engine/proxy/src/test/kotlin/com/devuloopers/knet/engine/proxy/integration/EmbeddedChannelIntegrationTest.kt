package com.devuloopers.knet.engine.proxy.integration

import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedChannelIntegrationTest {

    @Test
    fun testEmbeddedChannelCreation() {
        val channel = EmbeddedChannel()
        assertTrue(channel.isOpen)
        channel.close()
    }
}
