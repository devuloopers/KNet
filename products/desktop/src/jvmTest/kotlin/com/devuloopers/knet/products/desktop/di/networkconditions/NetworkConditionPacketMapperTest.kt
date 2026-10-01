package com.devuloopers.knet.products.desktop.di.networkconditions

import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
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
}
