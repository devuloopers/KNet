package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.traffic.model.AppliedNetworkConditionSource
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertContentEquals

class NetworkConditionChannelHandlerTest {
    @Test
    fun `http payload is forwarded incrementally after the aggregate bandwidth reservation`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val channel = EmbeddedChannel(
            NetworkConditionChannelHandler(NetworkConditionEngine(configuration = { configuration })),
        )
        val request = DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "https://video.example/upload")
        channel.writeInbound(request)
        assertNotNull(channel.readInbound<DefaultHttpRequest>())
        val evidence = assertNotNull(
            channel.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get(),
        )
        assertEquals(NetworkConditionBuiltIns.STREAMING_100_KBPS.id.value, evidence.profileId)
        assertEquals(AppliedNetworkConditionSource.GLOBAL, evidence.source)

        val content = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))
        channel.writeInbound(content)

        // Upload is unlimited in the streaming preset, so it remains a zero-copy immediate path.
        assertEquals(content, channel.readInbound<DefaultHttpContent>())

        val response = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))
        channel.writeOutbound(response)
        assertNull(channel.readOutbound<DefaultHttpContent>())
        channel.advanceTimeBy(101, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()
        assertEquals(response, channel.readOutbound<DefaultHttpContent>())
        channel.finishAndReleaseAll()
    }

    @Test
    fun `a live disable immediately releases bytes already queued on the flow`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val configuration = MutableStateFlow(NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        ))
        val channel = EmbeddedChannel(
            NetworkConditionChannelHandler(NetworkConditionEngine(configuration, scope)),
        )
        channel.writeInbound(
            DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "https://video.example/stream"),
        )
        channel.readInbound<DefaultHttpRequest>()

        val queued = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250) { 1 }))
        channel.writeOutbound(queued)
        configuration.value = configuration.value.copy(enabled = false)
        channel.runPendingTasks()

        // No subsequent network write is required to observe the live disable.
        assertSame(queued, channel.readOutbound<DefaultHttpContent>())
        channel.finishAndReleaseAll()
        scope.cancel()
    }

    @Test
    fun `a live rate edit replans bytes already queued on the flow`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fast = NetworkConditionProfile(
            id = NetworkConditionProfileId("fast-live-test"),
            name = "Fast live test",
            download = NetworkDirectionCondition(bitsPerSecond = 1_000_000L),
        )
        val configuration = MutableStateFlow(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
                customProfiles = listOf(fast),
            ),
        )
        val channel = EmbeddedChannel(
            NetworkConditionChannelHandler(NetworkConditionEngine(configuration, scope)),
        )
        channel.writeInbound(
            DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "https://video.example/stream"),
        )
        channel.readInbound<DefaultHttpRequest>()

        val queued = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))
        channel.writeOutbound(queued)
        configuration.value = configuration.value.copy(globalProfileId = fast.id)
        channel.runPendingTasks()
        assertNull(channel.readOutbound<DefaultHttpContent>())

        channel.advanceTimeBy(11, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()
        assertSame(queued, channel.readOutbound<DefaultHttpContent>())
        channel.finishAndReleaseAll()
        scope.cancel()
    }

    @Test
    fun `virtual MTU releases a large HTTP body incrementally without losing trailers`() {
        var nowNanos = 0L
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val channel = EmbeddedChannel(
            NetworkConditionChannelHandler(
                NetworkConditionEngine(configuration = { configuration }, nanoTime = { nowNanos }),
            ),
        )
        channel.writeInbound(
            DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "https://video.example/segment"),
        )
        channel.readInbound<DefaultHttpRequest>()
        val payload = ByteArray(3_000) { index -> (index % 127).toByte() }
        val last = DefaultLastHttpContent(Unpooled.wrappedBuffer(payload)).apply {
            trailingHeaders().set("x-stream-end", "yes")
        }

        channel.writeOutbound(last)
        nowNanos = TimeUnit.MILLISECONDS.toNanos(121)
        channel.advanceTimeBy(121, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()
        val first = assertNotNull(channel.readOutbound<DefaultHttpContent>())
        assertEquals(1_500, first.content().readableBytes())
        assertNull(channel.readOutbound<DefaultHttpContent>())

        nowNanos = TimeUnit.MILLISECONDS.toNanos(242)
        channel.advanceTimeBy(121, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()
        val second = assertNotNull(channel.readOutbound<DefaultLastHttpContent>())
        assertEquals("yes", second.trailingHeaders()["x-stream-end"])
        val combined = ByteArray(3_000)
        first.content().readBytes(combined, 0, 1_500)
        second.content().readBytes(combined, 1_500, 1_500)
        assertContentEquals(payload, combined)
        first.release()
        second.release()
        channel.finishAndReleaseAll()
    }
}
