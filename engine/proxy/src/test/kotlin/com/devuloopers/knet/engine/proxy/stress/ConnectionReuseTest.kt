package com.devuloopers.knet.engine.proxy.stress

import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionReuseTest {

    @Test
    fun testEmbeddedChannelActiveReuse() {
        val channel = EmbeddedChannel()
        assertTrue(channel.isActive)
        channel.close()
    }
}