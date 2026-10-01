package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
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
        assertEquals(6L, engine.snapshot.value.queuedBytes)
        engine.delivered(NetworkConditionDirection.DOWNLOAD, 6)
        assertEquals(0L, engine.snapshot.value.queuedBytes)
        assertEquals(6L, engine.snapshot.value.downloadedBytes)
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

    private fun profile(id: String, rate: Long) = NetworkConditionProfile(
        id = NetworkConditionProfileId(id),
        name = id,
        download = NetworkDirectionCondition(rate),
    )
}
