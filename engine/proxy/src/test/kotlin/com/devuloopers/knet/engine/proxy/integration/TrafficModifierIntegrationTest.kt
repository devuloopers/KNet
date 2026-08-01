package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.proxy.KNetProxyServer
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficModifierIntegrationTest {

    @Test
    fun testPipelineInitializerRegistrationForTrafficModifier() {
        var initializerInvoked = false
        val customInitializer: (io.netty.channel.ChannelPipeline) -> Unit = { _ ->
            initializerInvoked = true
        }

        KNetProxyServer.pipelineInitializers.add(customInitializer)
        assertTrue(KNetProxyServer.pipelineInitializers.contains(customInitializer))

        // Create embedded channel pipeline and execute registered initializers
        val channel = EmbeddedChannel()
        KNetProxyServer.pipelineInitializers.forEach { it(channel.pipeline()) }
        assertTrue(initializerInvoked)

        KNetProxyServer.pipelineInitializers.remove(customInitializer)
        channel.close()
    }
}
