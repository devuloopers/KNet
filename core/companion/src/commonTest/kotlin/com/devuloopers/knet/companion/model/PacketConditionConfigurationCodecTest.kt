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
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(duplicationPercent = -1) }
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(reorderingPercent = 101) }
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(uploadBitsPerSecond = 999L) }
        assertFailsWith<IllegalArgumentException> {
            PacketConditionConfiguration(downloadBitsPerSecond = 100_000_000_001L)
        }
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(latencyMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(jitterMillis = 120_001L) }
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(maximumQueuedDatagrams = 0) }
        assertFailsWith<IllegalArgumentException> { PacketConditionConfiguration(maximumQueuedDatagrams = 16_385) }
    }

    @Test
    fun `duplicated wire fields fail closed`() {
        val encoded = PacketConditionConfigurationCodec.encode(PacketConditionConfiguration.Disabled)
        assertFailsWith<IllegalArgumentException> {
            PacketConditionConfigurationCodec.decode(encoded + "enabled=true\n".encodeToByteArray())
        }
    }

    @Test
    fun `missing unknown oversized and future wire formats fail closed`() {
        val encoded = PacketConditionConfigurationCodec.encode(PacketConditionConfiguration.Disabled).decodeToString()
        assertFailsWith<IllegalArgumentException> {
            PacketConditionConfigurationCodec.decode(encoded.replace("version=1", "version=2").encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            PacketConditionConfigurationCodec.decode(
                encoded.lineSequence().filterNot { it.startsWith("seed=") }.joinToString("\n").encodeToByteArray(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PacketConditionConfigurationCodec.decode((encoded + "unknown=value\n").encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            PacketConditionConfigurationCodec.decode(ByteArray(4_097) { 'x'.code.toByte() })
        }
    }
}
