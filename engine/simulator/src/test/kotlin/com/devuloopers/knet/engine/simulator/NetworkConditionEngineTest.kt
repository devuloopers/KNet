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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetworkConditionEngineTest {
    @Test
    fun `100 kbps download reservation produces sustained aggregate timing`() {
        var now = 0L
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val engine = NetworkConditionEngine({ configuration }, nanoTime = { now })

        val first = assertIs<NetworkConditionPlan.Delay>(
            engine.plan("video.example", 443, NetworkConditionDirection.DOWNLOAD, 12_500, 1),
        )
        val second = assertIs<NetworkConditionPlan.Delay>(
            engine.plan("video.example", 443, NetworkConditionDirection.DOWNLOAD, 12_500, 2),
        )

        assertEquals(1_000_000_000L, first.nanoseconds)
        assertEquals(2_000_000_000L, second.nanoseconds)
    }

    @Test
    fun `one minute of 100 kbps chunks remains smooth without burst credit`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val engine = NetworkConditionEngine({ configuration }, nanoTime = { 0L })

        repeat(600) { index ->
            val plan = assertIs<NetworkConditionPlan.Delay>(
                engine.plan(
                    "video.example",
                    443,
                    NetworkConditionDirection.DOWNLOAD,
                    1_250,
                    index.toLong() + 1L,
                ),
            )
            assertEquals((index + 1L) * 100_000_000L, plan.nanoseconds)
        }
    }

    @Test
    fun `parallel flows share one effective rule budget while upload stays unlimited`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(
                NetworkConditionRule(
                    NetworkConditionRuleId("video"),
                    NetworkConditionTarget.parse("*.example.com"),
                    NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
                ),
            ),
        )
        val engine = NetworkConditionEngine({ configuration }, nanoTime = { 0L })

        val one = assertIs<NetworkConditionPlan.Delay>(
            engine.plan("a.example.com", 443, NetworkConditionDirection.DOWNLOAD, 6_250, 1),
        )
        val two = assertIs<NetworkConditionPlan.Delay>(
            engine.plan("b.example.com", 443, NetworkConditionDirection.DOWNLOAD, 6_250, 2),
        )

        assertEquals(500_000_000L, one.nanoseconds)
        assertEquals(1_000_000_000L, two.nanoseconds)
        assertIs<NetworkConditionPlan.PassThrough>(
            engine.plan("a.example.com", 443, NetworkConditionDirection.UPLOAD, 6_250, 3),
        )
    }

    @Test
    fun `live rate change rebases stale budget immediately`() {
        var current = profile("condition", 100_000)
        val engine = NetworkConditionEngine(
            configuration = {
                NetworkConditionConfiguration(
                    enabled = true,
                    globalProfileId = current.id,
                    customProfiles = listOf(current),
                )
            },
            nanoTime = { 0L },
        )
        assertEquals(
            1_000_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("stream.example", 443, NetworkConditionDirection.DOWNLOAD, 12_500, 1),
            ).nanoseconds,
        )

        current = profile("condition", 1_000_000)

        assertEquals(
            100_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("stream.example", 443, NetworkConditionDirection.DOWNLOAD, 12_500, 2),
            ).nanoseconds,
        )
    }

    @Test
    fun `queue is globally bounded and telemetry contains no payload`() {
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() }, maximumQueuedBytes = 10)
        assertTrue(engine.tryQueue(6))
        assertEquals(false, engine.tryQueue(5))
        engine.refreshTelemetry()
        assertEquals(6L, engine.snapshot.value.queuedBytes)
        engine.releaseQueued(6)
        engine.recordForwarded(NetworkConditionDirection.DOWNLOAD, 6)
        engine.refreshTelemetry()
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(6L, engine.snapshot.value.downloadedBytes)
    }

    @Test
    fun `observed forwarding counts shaped and pass-through payload without queue coupling`() {
        var now = 42L
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() }, nanoTime = { now })

        engine.recordForwarded(NetworkConditionDirection.UPLOAD, 120)
        engine.recordForwarded(NetworkConditionDirection.DOWNLOAD, 480)
        engine.refreshTelemetry()

        assertEquals(42L, engine.snapshot.value.sampledAtNanos)
        assertEquals(120L, engine.snapshot.value.uploadedBytes)
        assertEquals(480L, engine.snapshot.value.downloadedBytes)
        assertEquals(0L, engine.snapshot.value.queuedBytes)

        now = 84L
        engine.abandon(400)
        engine.refreshTelemetry()
        assertEquals(120L, engine.snapshot.value.uploadedBytes)
        assertEquals(480L, engine.snapshot.value.downloadedBytes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `runtime snapshots are sampled while observed and stop without a subscriber`() = runTest {
        var nowNanos = 1L
        val engine = NetworkConditionEngine(
            configuration = MutableStateFlow(NetworkConditionConfiguration()),
            scope = backgroundScope,
            nanoTime = { nowNanos },
        )
        val samples = mutableListOf<Long>()
        val observer = backgroundScope.launch {
            engine.snapshot.collect { snapshot -> samples += snapshot.downloadedBytes }
        }
        runCurrent()
        engine.recordForwarded(NetworkConditionDirection.DOWNLOAD, 12_500)
        nowNanos += 1_000_000_000L
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(12_500L, samples.last())
        val observedSamples = samples.size

        observer.cancel()
        runCurrent()
        engine.recordForwarded(NetworkConditionDirection.DOWNLOAD, 12_500)
        nowNanos += 1_000_000_000L
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(observedSamples, samples.size)
        assertEquals(12_500L, engine.snapshot.value.downloadedBytes)
    }

    @Test
    fun `MTU-only profile is active and exposes its virtual MTU`() {
        val profile = NetworkConditionProfile(
            id = NetworkConditionProfileId("mtu-only"),
            name = "MTU only",
            virtualMtuBytes = 900,
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = profile.id,
            customProfiles = listOf(profile),
        )
        val engine = NetworkConditionEngine({ configuration })

        assertEquals(false, profile.isPassThrough)
        assertEquals(900, engine.virtualMtu("stream.example", 443))
    }

    @Test
    fun `utilization idle periods and independent rule budgets preserve aggregate semantics`() {
        var now = 0L
        val limited = NetworkConditionProfile(
            id = NetworkConditionProfileId("utilized"),
            name = "Utilized",
            download = NetworkDirectionCondition(100_000L, 80),
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            customProfiles = listOf(limited),
            rules = listOf(
                NetworkConditionRule(NetworkConditionRuleId("one"), NetworkConditionTarget.parse("one.test"), limited.id),
                NetworkConditionRule(NetworkConditionRuleId("two"), NetworkConditionTarget.parse("two.test"), limited.id),
            ),
        )
        val engine = NetworkConditionEngine({ configuration }, nanoTime = { now })

        assertEquals(
            125_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("one.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 1L),
            ).nanoseconds,
        )
        assertEquals(
            125_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("two.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 2L),
            ).nanoseconds,
        )

        now = 10_000_000_000L
        assertEquals(
            125_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("one.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 3L),
            ).nanoseconds,
        )
    }

    @Test
    fun `latency and jitter are deterministic bounded and never reorder reservations`() {
        val delayed = NetworkConditionProfile(
            id = NetworkConditionProfileId("delayed"),
            name = "Delayed",
            latencyMillis = 100L,
            jitterMillis = 20L,
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = delayed.id,
            customProfiles = listOf(delayed),
        )
        val first = NetworkConditionEngine({ configuration }, nanoTime = { 0L })
        val second = NetworkConditionEngine({ configuration }, nanoTime = { 0L })

        val firstDelays = (1L..100L).map { sequence ->
            assertIs<NetworkConditionPlan.Delay>(
                first.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, 1, sequence),
            ).nanoseconds
        }
        val secondDelays = (1L..100L).map { sequence ->
            assertIs<NetworkConditionPlan.Delay>(
                second.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, 1, sequence),
            ).nanoseconds
        }

        assertEquals(firstDelays, secondDelays)
        assertTrue(firstDelays.all { it in 80_000_000L..120_000_000L })
        assertTrue(firstDelays.distinct().size > 1)
    }

    @Test
    fun `fixed latency applies once per protocol unit instead of compounding across a stream`() {
        val delayed = NetworkConditionProfile(
            id = NetworkConditionProfileId("fixed-latency"),
            name = "Fixed latency",
            latencyMillis = 100L,
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = delayed.id,
            customProfiles = listOf(delayed),
        )
        val engine = NetworkConditionEngine({ configuration }, nanoTime = { 0L })

        (1L..3L).forEach { sequence ->
            assertEquals(
                100_000_000L,
                assertIs<NetworkConditionPlan.Delay>(
                    engine.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, sequence),
                ).nanoseconds,
            )
        }
    }

    @Test
    fun `offline reset timeout and seeded faults publish deterministic outcomes`() {
        listOf(
            NetworkFailureBehavior.Offline,
            NetworkFailureBehavior.ResetFlow,
            NetworkFailureBehavior.Timeout,
        ).forEach { failure ->
            val condition = profile("fault-${failure::class.simpleName?.lowercase()}", 100_000L).copy(failure = failure)
            val engine = NetworkConditionEngine(configuration = {
                NetworkConditionConfiguration(
                    enabled = true,
                    globalProfileId = condition.id,
                    customProfiles = listOf(condition),
                )
            })
            assertEquals(failure, assertIs<NetworkConditionPlan.Fail>(
                engine.plan("fault.test", 443, NetworkConditionDirection.DOWNLOAD, 10, 1L),
            ).behavior)
        }

        val seeded = profile("seeded", 100_000L).copy(
            failure = NetworkFailureBehavior.SeededReset(probabilityPercent = 37, seed = 42L),
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = seeded.id,
            customProfiles = listOf(seeded),
        )
        fun decisions() = NetworkConditionEngine({ configuration }).let { engine ->
            (1L..1_000L).map { sequence ->
                engine.plan("fault.test", 443, NetworkConditionDirection.DOWNLOAD, 1, sequence) is NetworkConditionPlan.Fail
            }
        }
        val one = decisions()
        val two = decisions()
        assertEquals(one, two)
        assertTrue(one.count { it } in 320..420)
    }

    @Test
    fun `queue and active flow counters remain bounded at exact edges`() {
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() }, maximumQueuedBytes = 10)
        assertTrue(engine.tryQueue(10))
        assertEquals(false, engine.tryQueue(1))
        engine.releaseQueued(10)
        engine.releaseQueued(10)
        val first = engine.openFlow()
        val second = engine.openFlow()
        engine.refreshTelemetry()
        assertEquals(2, engine.snapshot.value.activeFlows)
        first.close()
        first.close()
        engine.refreshTelemetry()
        assertEquals(1, engine.snapshot.value.activeFlows)
        second.close()
        engine.refreshTelemetry()
        assertEquals(0, engine.snapshot.value.activeFlows)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
    }

    @Test
    fun `default aggregate queue accepts exactly sixty four MiB and rejects the next byte`() {
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() })
        val maximum = NetworkConditionEngine.DEFAULT_MAXIMUM_QUEUED_BYTES

        assertTrue(engine.tryQueue(maximum.toInt()))
        assertEquals(false, engine.tryQueue(1))
        engine.refreshTelemetry()
        assertEquals(maximum, engine.snapshot.value.queuedBytes)

        engine.releaseQueued(maximum.toInt())
        engine.refreshTelemetry()
        assertEquals(0L, engine.snapshot.value.queuedBytes)
    }

    @Test
    fun `delay arithmetic saturates instead of wrapping near long max`() {
        val limited = profile("overflow-safe", 1_000L)
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = limited.id,
            customProfiles = listOf(limited),
        )
        val engine = NetworkConditionEngine({ configuration }, nanoTime = { Long.MAX_VALUE - 10L })

        val delay = assertIs<NetworkConditionPlan.Delay>(
            engine.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, Int.MAX_VALUE, 1L),
        ).nanoseconds

        assertTrue(delay > 0L)
    }

    @Test
    fun `repeated and backward clocks never produce a negative or immediate reservation`() {
        var now = 1_000_000_000L
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val engine = NetworkConditionEngine({ configuration }, nanoTime = { now })

        assertEquals(
            100_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 1L),
            ).nanoseconds,
        )
        assertEquals(
            200_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 2L),
            ).nanoseconds,
        )

        now = 500_000_000L
        assertEquals(
            800_000_000L,
            assertIs<NetworkConditionPlan.Delay>(
                engine.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 3L),
            ).nanoseconds,
        )
    }

    private fun profile(id: String, rate: Long) = NetworkConditionProfile(
        id = NetworkConditionProfileId(id),
        name = id,
        download = NetworkDirectionCondition(rate),
    )
}
