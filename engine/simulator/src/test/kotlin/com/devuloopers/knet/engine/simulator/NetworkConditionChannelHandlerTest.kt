package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.traffic.model.AppliedNetworkConditionSource
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
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
import kotlin.test.assertTrue

class NetworkConditionChannelHandlerTest {
    @Test
    fun `request without a trustworthy destination bypasses the global condition`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.OFFLINE.id,
        )
        val channel = EmbeddedChannel(
            NetworkConditionChannelHandler(NetworkConditionEngine(configuration = { configuration })),
        )
        val request = DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/relative")

        channel.writeInbound(request)

        assertSame(request, channel.readInbound<DefaultHttpRequest>())
        assertNull(channel.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get())
        assertTrue(channel.isActive)
        channel.finishAndReleaseAll()
    }

    @Test
    fun `http payload is forwarded incrementally after the aggregate bandwidth reservation`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(
            NetworkConditionChannelHandler(engine),
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
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.uploadedBytes)
        assertEquals(0L, engine.snapshot.value.downloadedBytes)
        assertEquals(1_250L, engine.snapshot.value.queuedBytes)
        channel.advanceTimeBy(101, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()
        assertEquals(response, channel.readOutbound<DefaultHttpContent>())
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        channel.finishAndReleaseAll()
    }

    @Test
    fun `a live disable immediately releases bytes already queued on the flow`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val configuration = MutableStateFlow(NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        ))
        val engine = NetworkConditionEngine(configuration, scope)
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine))
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
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
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
    fun `a live latency and jitter edit replans queued bytes without losing the payload`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val slow = NetworkConditionProfile(
            id = NetworkConditionProfileId("latency-slow"),
            name = "Latency slow",
            latencyMillis = 100L,
            jitterMillis = 20L,
        )
        val fast = slow.copy(
            id = NetworkConditionProfileId("latency-fast"),
            name = "Latency fast",
            latencyMillis = 10L,
            jitterMillis = 0L,
        )
        val configuration = MutableStateFlow(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = slow.id,
                customProfiles = listOf(slow, fast),
            ),
        )
        val engine = NetworkConditionEngine(configuration, scope)
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))

        channel.writeOutbound(payload)
        configuration.value = configuration.value.copy(globalProfileId = fast.id)
        channel.runPendingTasks()
        assertNull(channel.readOutbound<DefaultHttpContent>())
        channel.advanceTimeBy(11, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()

        assertSame(payload, channel.readOutbound<DefaultHttpContent>())
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        payload.release()
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
        val engine = NetworkConditionEngine(configuration = { configuration }, nanoTime = { nowNanos })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine))
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
        engine.refreshTelemetry()
        assertEquals(3_000L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        first.release()
        second.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `faulted payload is never reported as observed throughput`() {
        val offline = NetworkConditionProfile(
            id = NetworkConditionProfileId("offline-test"),
            name = "Offline test",
            failure = NetworkFailureBehavior.Offline,
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = offline.id,
            customProfiles = listOf(offline),
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }

        runCatching {
            channel.writeOutbound(DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250))))
        }
        engine.refreshTelemetry()

        assertEquals(0L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        channel.finishAndReleaseAll()
    }

    @Test
    fun `timeout fails the unit without closing the flow while reset closes it once`() {
        listOf(
            NetworkFailureBehavior.Timeout to true,
            NetworkFailureBehavior.ResetFlow to false,
        ).forEachIndexed { index, (failure, remainsActive) ->
            val profile = NetworkConditionProfile(
                id = NetworkConditionProfileId("failure-handler-$index"),
                name = "Failure handler $index",
                failure = failure,
            )
            val configuration = NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = profile.id,
                customProfiles = listOf(profile),
            )
            val engine = NetworkConditionEngine(configuration = { configuration })
            val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
                attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
                attr(ProxyChannelAttributes.PORT).set(443)
            }
            val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(10)))

            val write = runCatching { channel.writeOutbound(payload) }
            engine.refreshTelemetry()

            assertTrue(write.isFailure)
            assertTrue(write.exceptionOrNull()?.message?.contains(failure::class.simpleName.orEmpty()) == true)
            assertEquals(0, payload.refCnt())
            assertEquals(remainsActive, channel.isActive)
            assertEquals(if (remainsActive) 1 else 0, engine.snapshot.value.activeFlows)
            assertEquals(0L, engine.snapshot.value.downloadedBytes)
            assertEquals(0L, engine.snapshot.value.queuedBytes)
            assertEquals(1L, engine.snapshot.value.faultedFlows)
            runCatching { channel.close() }
        }
    }

    @Test
    fun `zero byte trailers preserve order behind a shaped payload without consuming bandwidth`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        var nowNanos = 0L
        val engine = NetworkConditionEngine(configuration = { configuration }, nanoTime = { nowNanos })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))
        val trailers = DefaultLastHttpContent(Unpooled.EMPTY_BUFFER).apply {
            trailingHeaders().set("X-Test-Trailer", "preserved")
        }

        channel.writeOutbound(payload)
        channel.writeOutbound(trailers)
        assertNull(channel.readOutbound<DefaultHttpContent>())
        nowNanos = TimeUnit.MILLISECONDS.toNanos(101L)
        channel.advanceTimeBy(101, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()
        channel.runScheduledPendingTasks()

        assertSame(payload, channel.readOutbound<DefaultHttpContent>())
        val forwardedTrailers = assertNotNull(channel.readOutbound<DefaultLastHttpContent>())
        assertSame(trailers, forwardedTrailers)
        assertEquals("preserved", forwardedTrailers.trailingHeaders()["X-Test-Trailer"])
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        payload.release()
        forwardedTrailers.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `per flow queue accepts exactly eight MiB then rejects and releases the next byte`() {
        val limited = NetworkConditionProfile(
            id = NetworkConditionProfileId("queue-boundary"),
            name = "Queue boundary",
            download = NetworkDirectionCondition(1_000L),
            virtualMtuBytes = NetworkConditionProfile.MAXIMUM_VIRTUAL_MTU_BYTES,
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = limited.id,
            customProfiles = listOf(limited),
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val queued = buildList {
            repeat(128) {
                add(DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(65_535))))
            }
            add(DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(128))))
        }

        queued.forEach(channel::writeOutbound)
        engine.refreshTelemetry()
        assertEquals(8L * 1_024L * 1_024L, engine.snapshot.value.queuedBytes)

        val rejected = DefaultHttpContent(Unpooled.wrappedBuffer(byteArrayOf(1)))
        runCatching { channel.writeOutbound(rejected) }
        engine.refreshTelemetry()

        assertEquals(0, rejected.refCnt())
        assertTrue(queued.all { it.refCnt() == 0 })
        assertEquals(false, channel.isActive)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(0L, engine.snapshot.value.downloadedBytes)
        assertEquals(0, engine.snapshot.value.activeFlows)
        assertEquals(1L, engine.snapshot.value.faultedFlows)
    }

    @Test
    fun `aggregate queue rejection faults only the rejected flow and preserves other flows`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val limited = NetworkConditionProfile(
            id = NetworkConditionProfileId("aggregate-boundary"),
            name = "Aggregate boundary",
            download = NetworkDirectionCondition(1_000L),
        )
        val configuration = MutableStateFlow(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = limited.id,
                customProfiles = listOf(limited),
            ),
        )
        val engine = NetworkConditionEngine(configuration, scope, maximumQueuedBytes = 10L)
        fun channel() = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val firstChannel = channel()
        val secondChannel = channel()
        val rejectedChannel = channel()
        val first = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(6) { 1 }))
        val second = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(4) { 2 }))
        val rejected = DefaultHttpContent(Unpooled.wrappedBuffer(byteArrayOf(3)))

        firstChannel.writeOutbound(first)
        secondChannel.writeOutbound(second)
        runCatching { rejectedChannel.writeOutbound(rejected) }
        engine.refreshTelemetry()

        assertTrue(firstChannel.isActive)
        assertTrue(secondChannel.isActive)
        assertEquals(false, rejectedChannel.isActive)
        assertEquals(1, first.refCnt())
        assertEquals(1, second.refCnt())
        assertEquals(0, rejected.refCnt())
        assertEquals(10L, engine.snapshot.value.queuedBytes)
        assertEquals(1L, engine.snapshot.value.faultedFlows)
        assertEquals(2, engine.snapshot.value.activeFlows)

        configuration.value = configuration.value.copy(enabled = false)
        firstChannel.runPendingTasks()
        secondChannel.runPendingTasks()
        assertSame(first, firstChannel.readOutbound<DefaultHttpContent>())
        assertSame(second, secondChannel.readOutbound<DefaultHttpContent>())
        engine.refreshTelemetry()
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(10L, engine.snapshot.value.downloadedBytes)

        first.release()
        second.release()
        firstChannel.finishAndReleaseAll()
        secondChannel.finishAndReleaseAll()
        scope.cancel()
    }

    @Test
    fun `no-throttling domain override bypasses a limited global profile and is still observed`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
            rules = listOf(
                NetworkConditionRule(
                    NetworkConditionRuleId("bypass"),
                    NetworkConditionTarget.parse("video.example"),
                    NetworkConditionBuiltIns.NO_THROTTLING.id,
                ),
            ),
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))

        channel.writeOutbound(payload)

        assertSame(payload, channel.readOutbound<DefaultHttpContent>())
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals("bypass", channel.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get()?.ruleId)
        payload.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `closing a channel abandons queued payload and releases flow accounting exactly once`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))
        channel.writeOutbound(payload)
        engine.refreshTelemetry()
        assertEquals(1, engine.snapshot.value.activeFlows)
        assertEquals(1_250L, engine.snapshot.value.queuedBytes)

        runCatching { channel.close() }
        engine.refreshTelemetry()

        assertEquals(0, payload.refCnt())
        assertEquals(0, engine.snapshot.value.activeFlows)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(0L, engine.snapshot.value.downloadedBytes)
    }

    @Test
    fun `removing the handler unregisters live edits cancels timers and releases its flow`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val configuration = MutableStateFlow(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
            ),
        )
        val engine = NetworkConditionEngine(configuration, scope)
        val handler = NetworkConditionChannelHandler(engine)
        val channel = EmbeddedChannel(handler).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))
        channel.writeOutbound(payload)
        engine.refreshTelemetry()
        assertEquals(1, engine.snapshot.value.activeFlows)
        assertEquals(1_250L, engine.snapshot.value.queuedBytes)

        runCatching { channel.pipeline().remove(handler) }
        configuration.value = configuration.value.copy(enabled = false)
        runCatching { channel.runPendingTasks() }
        channel.advanceTimeBy(1, TimeUnit.SECONDS)
        runCatching { channel.runScheduledPendingTasks() }
        engine.refreshTelemetry()

        assertEquals(0, payload.refCnt())
        assertEquals(0, engine.snapshot.value.activeFlows)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(0L, engine.snapshot.value.downloadedBytes)
        assertNull(channel.readOutbound<DefaultHttpContent>())
        runCatching { channel.close() }
        scope.cancel()
    }

    @Test
    fun `live disable releases multiple queued messages in direction order without double counting`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val configuration = MutableStateFlow(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
            ),
        )
        val engine = NetworkConditionEngine(configuration, scope)
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val first = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250) { 1 }))
        val second = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250) { 2 }))
        channel.writeOutbound(first)
        channel.writeOutbound(second)
        assertNull(channel.readOutbound<DefaultHttpContent>())

        configuration.value = configuration.value.copy(enabled = false)
        channel.runPendingTasks()

        assertSame(first, channel.readOutbound<DefaultHttpContent>())
        assertSame(second, channel.readOutbound<DefaultHttpContent>())
        engine.refreshTelemetry()
        assertEquals(2_500L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertTrue(channel.isActive)
        first.release()
        second.release()
        channel.finishAndReleaseAll()
        scope.cancel()
    }

    @Test
    fun `repeated enable disable cycles converge without duplicate releases or leaked queues`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val configuration = MutableStateFlow(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
            ),
        )
        val engine = NetworkConditionEngine(configuration, scope)
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }

        repeat(25) { cycle ->
            val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250) { cycle.toByte() }))
            channel.writeOutbound(payload)
            assertNull(channel.readOutbound<DefaultHttpContent>())
            configuration.value = configuration.value.copy(enabled = false)
            channel.runPendingTasks()
            assertSame(payload, channel.readOutbound<DefaultHttpContent>())
            payload.release()
            configuration.value = configuration.value.copy(enabled = true)
            channel.runPendingTasks()
        }

        engine.refreshTelemetry()
        assertEquals(31_250L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(1, engine.snapshot.value.activeFlows)
        channel.finishAndReleaseAll()
        engine.refreshTelemetry()
        assertEquals(0, engine.snapshot.value.activeFlows)
        scope.cancel()
    }

    @Test
    fun `upload and download queues progress independently with exact direction counters`() {
        var nowNanos = 0L
        val bidirectional = NetworkConditionProfile(
            id = NetworkConditionProfileId("bidirectional"),
            name = "Bidirectional",
            upload = NetworkDirectionCondition(100_000L),
            download = NetworkDirectionCondition(100_000L),
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = bidirectional.id,
            customProfiles = listOf(bidirectional),
        )
        val engine = NetworkConditionEngine(configuration = { configuration }, nanoTime = { nowNanos })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val upload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250) { 1 }))
        val download = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250) { 2 }))

        channel.writeInbound(upload)
        channel.writeOutbound(download)
        assertNull(channel.readInbound<DefaultHttpContent>())
        assertNull(channel.readOutbound<DefaultHttpContent>())
        engine.refreshTelemetry()
        assertEquals(2_500L, engine.snapshot.value.queuedBytes)

        nowNanos = TimeUnit.MILLISECONDS.toNanos(101L)
        channel.advanceTimeBy(101L, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()

        assertSame(upload, channel.readInbound<DefaultHttpContent>())
        assertSame(download, channel.readOutbound<DefaultHttpContent>())
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.uploadedBytes)
        assertEquals(1_250L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        upload.release()
        download.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `opaque protocol frame retains type payload and identity after shaping`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("socket.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val bytes = ByteArray(1_250) { index -> (index % 127).toByte() }
        val frame = BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes))

        channel.writeOutbound(frame)
        assertNull(channel.readOutbound<BinaryWebSocketFrame>())
        channel.advanceTimeBy(101, TimeUnit.MILLISECONDS)
        channel.runScheduledPendingTasks()

        val forwarded = assertNotNull(channel.readOutbound<BinaryWebSocketFrame>())
        assertSame(frame, forwarded)
        val actual = ByteArray(forwarded.content().readableBytes())
        forwarded.content().getBytes(forwarded.content().readerIndex(), actual)
        assertContentEquals(bytes, actual)
        engine.refreshTelemetry()
        assertEquals(1_250L, engine.snapshot.value.downloadedBytes)
        forwarded.release()
        channel.finishAndReleaseAll()
    }

    @Test
    fun `switching a queued flow offline releases buffers and a recovered new flow can pass`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val configuration = MutableStateFlow(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
            ),
        )
        val engine = NetworkConditionEngine(configuration, scope)
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val queued = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(1_250)))
        channel.writeOutbound(queued)
        configuration.value = configuration.value.copy(globalProfileId = NetworkConditionBuiltIns.OFFLINE.id)
        runCatching { channel.runPendingTasks() }

        assertEquals(0, queued.refCnt())
        engine.refreshTelemetry()
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(0L, engine.snapshot.value.downloadedBytes)
        assertEquals(false, channel.isActive)

        configuration.value = configuration.value.copy(
            globalProfileId = NetworkConditionBuiltIns.NO_THROTTLING.id,
        )
        val recovered = EmbeddedChannel(NetworkConditionChannelHandler(engine)).apply {
            attr(ProxyChannelAttributes.ROUTE_HOST).set("video.example")
            attr(ProxyChannelAttributes.PORT).set(443)
        }
        val payload = DefaultHttpContent(Unpooled.wrappedBuffer(ByteArray(10)))
        recovered.writeOutbound(payload)
        assertSame(payload, recovered.readOutbound<DefaultHttpContent>())
        payload.release()
        recovered.finishAndReleaseAll()
        scope.cancel()
    }
}
