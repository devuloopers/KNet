package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkThroughputPresentationTest {
    @Test
    fun `reference labels never present a mixed or bypassed aggregate as one configured limit`() {
        assertEquals("Conditions bypassed", throughputReference(NetworkConditionsState()).label)
        assertEquals(
            "No global limit",
            throughputReference(
                NetworkConditionsState(configuration = NetworkConditionConfiguration(enabled = true)),
            ).label,
        )
        assertEquals(
            "Unlimited",
            throughputReference(stateWithGlobal(NetworkConditionBuiltIns.NO_THROTTLING.id.value)).label,
        )
        assertEquals(
            "Offline",
            throughputReference(stateWithGlobal(NetworkConditionBuiltIns.OFFLINE.id.value)).label,
        )
        val streaming = throughputReference(stateWithGlobal(NetworkConditionBuiltIns.STREAMING_100_KBPS.id.value))
        assertEquals("Configured reference", streaming.label)
        assertEquals(100_000L, streaming.downloadBitsPerSecond)
        assertEquals(null, streaming.uploadBitsPerSecond)

        val mixed = NetworkConditionsState(
            configuration = NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
                rules = listOf(
                    NetworkConditionRule(
                        NetworkConditionRuleId("domain"),
                        NetworkConditionTarget.parse("video.example"),
                        NetworkConditionBuiltIns.SLOW_3G.id,
                    ),
                ),
            ),
        )
        assertEquals("Mixed profiles", throughputReference(mixed).label)
        assertEquals(null, throughputReference(mixed).downloadBitsPerSecond)
    }

    @Test
    fun `disabled domain rules do not turn the graph into a mixed aggregate`() {
        val state = NetworkConditionsState(
            configuration = NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
                rules = listOf(
                    NetworkConditionRule(
                        NetworkConditionRuleId("disabled"),
                        NetworkConditionTarget.parse("video.example"),
                        NetworkConditionBuiltIns.SLOW_3G.id,
                        enabled = false,
                    ),
                ),
            ),
        )

        assertEquals("Configured reference", throughputReference(state).label)
        assertEquals(100_000L, throughputReference(state).downloadBitsPerSecond)
    }

    @Test
    fun `chart maximum is positive finite and leaves headroom across rate scales`() {
        listOf(0L, 1L, 999L, 100_000L, 1_000_000L, 20_000_000L, Long.MAX_VALUE).forEach { value ->
            val maximum = niceChartMaximum(value)
            assertTrue(maximum > 0L)
            if (value in 1 until Long.MAX_VALUE) assertTrue(maximum >= value)
        }
        assertEquals(100_000L, niceChartMaximum(0L))
        assertEquals(125_000L, niceChartMaximum(100_000L))
    }

    @Test
    fun `observed traffic requires a valid non-zero sample`() {
        assertFalse(NetworkThroughputHistory().hasObservedTraffic())
        val sampler = NetworkThroughputSampler(capacity = 3)
        sampler.accept(snapshot(1_000_000_000L, 0L))
        val idle = requireNotNull(sampler.accept(snapshot(2_000_000_000L, 0L)))
        assertFalse(idle.hasObservedTraffic())
        val active = requireNotNull(sampler.accept(snapshot(3_000_000_000L, 12_500L)))
        assertTrue(active.hasObservedTraffic())
    }

    private fun stateWithGlobal(profileId: String) = NetworkConditionsState(
        configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId(profileId),
        ),
    )

    private fun snapshot(time: Long, downloaded: Long) = NetworkConditionRuntimeSnapshot(
        sampledAtNanos = time,
        downloadedBytes = downloaded,
    )
}
