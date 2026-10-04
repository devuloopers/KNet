package com.devuloopers.knet.products.desktop.di.networkconditions

import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkConditionPacketMapperTest {
    @Test
    fun `global custom profile retains asymmetric rate and packet condition axes`() {
        val profile = NetworkConditionProfile(
            id = NetworkConditionProfileId("packet-stream"),
            name = "Packet stream",
            download = NetworkDirectionCondition(100_000L, 80),
            upload = NetworkDirectionCondition(),
            latencyMillis = 40L,
            jitterMillis = 5L,
            packetLossPercent = 4,
            packetDuplicationPercent = 2,
            packetReorderingPercent = 3,
        )

        val packet = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = profile.id,
            customProfiles = listOf(profile),
        ).toPacketConditionConfiguration()

        assertTrue(packet.enabled)
        assertEquals(80_000L, packet.downloadBitsPerSecond)
        assertEquals(null, packet.uploadBitsPerSecond)
        assertEquals(40L, packet.latencyMillis)
        assertEquals(5L, packet.jitterMillis)
        assertEquals(4, packet.lossPercent)
        assertEquals(2, packet.duplicationPercent)
        assertEquals(3, packet.reorderingPercent)
    }

    @Test
    fun `disabled desktop conditions fail closed to disabled packet shaping`() {
        assertFalse(NetworkConditionConfiguration().toPacketConditionConfiguration().enabled)
    }

    @Test
    fun `offline timeout reset and seeded proxy faults map to deterministic packet loss`() {
        listOf(
            NetworkFailureBehavior.Offline,
            NetworkFailureBehavior.Timeout,
            NetworkFailureBehavior.ResetFlow,
        ).forEachIndexed { index, failure ->
            val profile = NetworkConditionProfile(
                id = NetworkConditionProfileId("failure-$index"),
                name = "Failure $index",
                failure = failure,
            )
            val packet = NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = profile.id,
                customProfiles = listOf(profile),
            ).toPacketConditionConfiguration()
            assertTrue(packet.enabled)
            assertEquals(100, packet.lossPercent)
        }

        val seeded = NetworkConditionProfile(
            id = NetworkConditionProfileId("seeded"),
            name = "Seeded",
            failure = NetworkFailureBehavior.SeededReset(37, 99L),
            packetLossPercent = 12,
        )
        val packet = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = seeded.id,
            customProfiles = listOf(seeded),
        ).toPacketConditionConfiguration()
        assertEquals(37, packet.lossPercent)
        assertEquals(99L, packet.seed)
    }

    @Test
    fun `packet-only profile stays enabled while no-effect pass-through stays disabled`() {
        val packetOnly = NetworkConditionProfile(
            id = NetworkConditionProfileId("packet-only"),
            name = "Packet only",
            packetReorderingPercent = 10,
        )
        assertTrue(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = packetOnly.id,
                customProfiles = listOf(packetOnly),
            ).toPacketConditionConfiguration().enabled,
        )

        assertFalse(
            NetworkConditionConfiguration(
                enabled = true,
                globalProfileId = com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
                    .NO_THROTTLING.id,
            ).toPacketConditionConfiguration().enabled,
        )
    }
}
