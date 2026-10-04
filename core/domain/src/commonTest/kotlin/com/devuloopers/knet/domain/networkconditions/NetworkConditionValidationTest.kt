package com.devuloopers.knet.domain.networkconditions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkConditionValidationTest {
    @Test
    fun `direction rate and utilization enforce every boundary`() {
        assertEquals(10L, NetworkDirectionCondition(1_000L, 1).effectiveBitsPerSecond)
        assertEquals(100_000_000_000L, NetworkDirectionCondition(100_000_000_000L, 100).effectiveBitsPerSecond)
        assertEquals(null, NetworkDirectionCondition(null, 1).effectiveBitsPerSecond)

        assertFailsWith<IllegalArgumentException> { NetworkDirectionCondition(999L) }
        assertFailsWith<IllegalArgumentException> { NetworkDirectionCondition(100_000_000_001L) }
        assertFailsWith<IllegalArgumentException> { NetworkDirectionCondition(1_000L, 0) }
        assertFailsWith<IllegalArgumentException> { NetworkDirectionCondition(1_000L, 101) }
    }

    @Test
    fun `profile delay mtu packet percentages and fault probability enforce boundaries`() {
        profile(latencyMillis = 0L)
        profile(latencyMillis = 120_000L)
        profile(jitterMillis = 0L)
        profile(jitterMillis = 120_000L)
        profile(virtualMtuBytes = 256)
        profile(virtualMtuBytes = 65_535)
        listOf(0, 100).forEach { boundary ->
            profile(packetLossPercent = boundary)
            profile(packetDuplicationPercent = boundary)
            profile(packetReorderingPercent = boundary)
        }
        NetworkFailureBehavior.SeededReset(1, 1L)
        NetworkFailureBehavior.SeededReset(100, 1L)

        assertFailsWith<IllegalArgumentException> { profile(latencyMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { profile(latencyMillis = 120_001L) }
        assertFailsWith<IllegalArgumentException> { profile(jitterMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { profile(jitterMillis = 120_001L) }
        assertFailsWith<IllegalArgumentException> { profile(virtualMtuBytes = 255) }
        assertFailsWith<IllegalArgumentException> { profile(virtualMtuBytes = 65_536) }
        listOf(-1, 101).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { profile(packetLossPercent = invalid) }
            assertFailsWith<IllegalArgumentException> { profile(packetDuplicationPercent = invalid) }
            assertFailsWith<IllegalArgumentException> { profile(packetReorderingPercent = invalid) }
        }
        assertFailsWith<IllegalArgumentException> { NetworkFailureBehavior.SeededReset(0, 1L) }
        assertFailsWith<IllegalArgumentException> { NetworkFailureBehavior.SeededReset(101, 1L) }
    }

    @Test
    fun `identifiers names and configuration references fail closed`() {
        assertFailsWith<IllegalArgumentException> { NetworkConditionProfileId("") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionProfileId(" Custom") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionProfileId("CUSTOM") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionProfileId("bad id") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionProfileId("-bad") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionProfileId("bad/") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionRuleId("  ") }
        assertFailsWith<IllegalArgumentException> { profile(name = " ") }

        val custom = profile(id = "custom")
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(customProfiles = listOf(custom, custom.copy()))
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(customProfiles = listOf(profile(id = "slow-3g")))
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(globalProfileId = NetworkConditionProfileId("missing"))
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(lastQuickAddProfileId = NetworkConditionProfileId("missing"))
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(
                rules = listOf(
                    NetworkConditionRule(
                        NetworkConditionRuleId("missing-profile"),
                        NetworkConditionTarget.parse("example.test"),
                        NetworkConditionProfileId("missing"),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(
                rules = listOf(
                    rule("same", "one.example"),
                    rule("same", "two.example"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(customProfiles = listOf(custom.copy(builtIn = true)))
        }
    }

    @Test
    fun `target parsing rejects malformed hosts wildcards unicode and port boundaries`() {
        listOf(
            "",
            "*.",
            "host..example",
            "bad_host.example",
            "[::1",
            "::1]",
            "example.test:443",
            "\uD800.example",
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>(invalid) { NetworkConditionTarget.parse(invalid) }
        }
        assertFailsWith<IllegalArgumentException> { NetworkConditionTarget.parse("*.127.0.0.1") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionTarget.parse("*.[::1]") }
        assertFailsWith<IllegalArgumentException> { NetworkConditionTarget.parse("example.test", 0) }
        assertFailsWith<IllegalArgumentException> { NetworkConditionTarget.parse("example.test", 65_536) }

        assertEquals("example.test:1", NetworkConditionTarget.parse(" Example.Test. ", 1).displayValue)
        assertEquals("*.example.test", NetworkConditionTarget.parse("  *.Example.Test.  ").displayValue)
        assertEquals("0:0:0:0:0:0:0:1:65535", NetworkConditionTarget.parse("[::1]", 65_535).displayValue)
    }

    @Test
    fun `packet-only effects remain active without becoming proxy shaping`() {
        val packetOnly = profile(packetLossPercent = 10)

        assertTrue(packetOnly.isPassThrough)
        assertTrue(packetOnly.hasPacketEffects)
        assertFalse(profile().hasPacketEffects)
    }

    private fun profile(
        id: String = "validation",
        name: String = "Validation",
        latencyMillis: Long = 0L,
        jitterMillis: Long = 0L,
        virtualMtuBytes: Int = NetworkConditionProfile.DEFAULT_VIRTUAL_MTU_BYTES,
        packetLossPercent: Int = 0,
        packetDuplicationPercent: Int = 0,
        packetReorderingPercent: Int = 0,
    ) = NetworkConditionProfile(
        id = NetworkConditionProfileId(id),
        name = name,
        latencyMillis = latencyMillis,
        jitterMillis = jitterMillis,
        virtualMtuBytes = virtualMtuBytes,
        packetLossPercent = packetLossPercent,
        packetDuplicationPercent = packetDuplicationPercent,
        packetReorderingPercent = packetReorderingPercent,
    )

    private fun rule(id: String, host: String) = NetworkConditionRule(
        NetworkConditionRuleId(id),
        NetworkConditionTarget.parse(host),
        NetworkConditionBuiltIns.NO_THROTTLING.id,
    )
}
