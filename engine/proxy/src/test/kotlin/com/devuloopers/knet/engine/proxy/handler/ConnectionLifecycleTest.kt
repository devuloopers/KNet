package com.devuloopers.knet.engine.proxy.handler

import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionLifecycleTest {

    @Test
    fun testChannelCloseLifecycle() {
        val channel = EmbeddedChannel()
        channel.close()
        assertFalse(channel.isOpen)
    }
}
