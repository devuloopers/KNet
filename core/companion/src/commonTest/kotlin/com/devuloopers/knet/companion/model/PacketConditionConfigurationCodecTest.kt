package com.devuloopers.knet.companion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PacketConditionConfigurationCodecTest {
    @Test
    fun `packet condition configuration round trips all qualified axes`() {
        val value = PacketConditionConfiguration(
            enabled = true,
            uploadBitsPerSecond = 250_000,
            downloadBitsPerSecond = 100_000,
            latencyMillis = 120,
            jitterMillis = 40,
            lossPercent = 5,
            duplicationPercent = 2,
            reorderingPercent = 3,
            seed = 42,
            maximumQueuedDatagrams = 256,
        )
        assertEquals(value, PacketConditionConfigurationCodec.decode(PacketConditionConfigurationCodec.encode(value)))
    }

    @Test
    fun `invalid packet percentages fail closed`() {
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(lossPercent = 101) }
    }

    @Test
    fun `duplicated wire fields fail closed`() {
        val encoded = PacketConditionConfigurationCodec.encode(PacketConditionConfiguration.Disabled)
        assertFailsWith<IllegalArgumentException> {
            PacketConditionConfigurationCodec.decode(encoded + "enabled=true\n".encodeToByteArray())
        }
    }
}
