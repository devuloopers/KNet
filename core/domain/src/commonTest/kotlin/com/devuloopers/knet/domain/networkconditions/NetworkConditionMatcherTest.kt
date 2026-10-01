package com.devuloopers.knet.domain.networkconditions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NetworkConditionMatcherTest {
    private val slow = NetworkConditionBuiltIns.STREAMING_100_KBPS.id
    private val bypass = NetworkConditionBuiltIns.NO_THROTTLING.id

    @Test
    fun `exact and port rules outrank wildcard and global rules`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.SLOW_3G.id,
            rules = listOf(
                rule("wild", "*.example.com", slow),
                rule("exact", "video.example.com", bypass),
                rule("port", "video.example.com", slow, 8443),
            ),
        )

        assertEquals("port", NetworkConditionMatcher.resolve(configuration, "VIDEO.EXAMPLE.COM.", 8443)?.ruleId?.value)
        assertEquals("exact", NetworkConditionMatcher.resolve(configuration, "video.example.com", 443)?.ruleId?.value)
        assertEquals("wild", NetworkConditionMatcher.resolve(configuration, "cdn.example.com", 443)?.ruleId?.value)
        assertEquals(EffectiveNetworkCondition.Source.GLOBAL, NetworkConditionMatcher.resolve(configuration, "example.com", 443)?.source)
    }

    @Test
    fun `longest wildcard suffix wins and does not include its apex`() {
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(
                rule("parent", "*.example.com", slow),
                rule("child", "*.video.example.com", bypass),
            ),
        )

        assertEquals("child", NetworkConditionMatcher.resolve(configuration, "a.video.example.com", 443)?.ruleId?.value)
        assertEquals("parent", NetworkConditionMatcher.resolve(configuration, "video.example.com", 443)?.ruleId?.value)
        assertNull(NetworkConditionMatcher.resolve(configuration, "example.com", 443))
    }

    @Test
    fun `normalization handles idna ipv4 ipv6 brackets and rejects duplicates`() {
        assertEquals("xn--bcher-kva.example", NetworkConditionTarget.parse("Bücher.Example.").normalizedHost)
        assertEquals("127.0.0.1", NetworkConditionTarget.parse("127.000.000.001").normalizedHost)
        assertEquals("0:0:0:0:0:0:0:1", NetworkConditionTarget.parse("[::1]").normalizedHost)
        assertFailsWith<IllegalArgumentException> { NetworkConditionTarget.parse("*.127.0.0.1") }
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionConfiguration(
                rules = listOf(
                    rule("one", "api.example.com", slow),
                    rule("two", "API.EXAMPLE.COM.", bypass),
                ),
            )
        }
    }

    @Test
    fun `disabled configuration bypasses all profiles`() {
        assertNull(
            NetworkConditionMatcher.resolve(
                NetworkConditionConfiguration(enabled = false, globalProfileId = slow),
                "video.example.com",
                443,
            ),
        )
    }

    private fun rule(
        id: String,
        host: String,
        profile: NetworkConditionProfileId,
        port: Int? = null,
    ) = NetworkConditionRule(
        NetworkConditionRuleId(id),
        NetworkConditionTarget.parse(host, port),
        profile,
    )
}
