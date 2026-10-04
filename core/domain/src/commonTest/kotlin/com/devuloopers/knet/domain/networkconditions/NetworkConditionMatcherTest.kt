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
        assertNull(NetworkConditionMatcher.resolve(configuration, "notexample.com", 443))
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

    @Test
    fun `disabled rules fall through and an enabled no-throttling rule bypasses global shaping`() {
        val disabledExact = rule("disabled", "video.example.com", slow).copy(enabled = false)
        val bypassRule = rule("bypass", "*.example.com", bypass)
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.SLOW_3G.id,
            rules = listOf(disabledExact, bypassRule),
        )

        val bypassed = NetworkConditionMatcher.resolve(configuration, "video.example.com", 443)
        assertEquals("bypass", bypassed?.ruleId?.value)
        assertEquals(NetworkConditionBuiltIns.NO_THROTTLING.id, bypassed?.profile?.id)
        assertEquals(
            NetworkConditionBuiltIns.SLOW_3G.id,
            NetworkConditionMatcher.resolve(configuration, "outside.test", 443)?.profile?.id,
        )
    }

    @Test
    fun `precedence is stable regardless of declaration order`() {
        val candidates = listOf(
            rule("wild", "*.example.com", bypass),
            rule("wild-port", "*.example.com", slow, 443),
            rule("long-wild", "*.video.example.com", bypass),
            rule("long-wild-port", "*.video.example.com", slow, 443),
            rule("exact", "cdn.video.example.com", bypass),
            rule("exact-port", "cdn.video.example.com", slow, 443),
        )

        listOf(candidates, candidates.reversed()).forEach { rules ->
            val configuration = NetworkConditionConfiguration(enabled = true, rules = rules)
            assertEquals(
                "exact-port",
                NetworkConditionMatcher.resolve(configuration, "cdn.video.example.com", 443)?.ruleId?.value,
            )
            assertEquals(
                "exact",
                NetworkConditionMatcher.resolve(configuration, "cdn.video.example.com", 8443)?.ruleId?.value,
            )
            assertEquals(
                "long-wild-port",
                NetworkConditionMatcher.resolve(configuration, "edge.video.example.com", 443)?.ruleId?.value,
            )
            assertEquals(
                "long-wild",
                NetworkConditionMatcher.resolve(configuration, "edge.video.example.com", 8443)?.ruleId?.value,
            )
            assertEquals(
                "wild-port",
                NetworkConditionMatcher.resolve(configuration, "edge.example.com", 443)?.ruleId?.value,
            )
            assertNull(NetworkConditionMatcher.resolve(configuration, "outside.test", 443))
        }

        val withGlobal = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.HIGH_LATENCY.id,
            rules = candidates,
        )
        assertEquals(
            EffectiveNetworkCondition.Source.GLOBAL,
            NetworkConditionMatcher.resolve(withGlobal, "outside.test", 443)?.source,
        )
    }

    @Test
    fun `matching semantic rule outranks an equally prioritized destination fallback`() {
        val graphQL = NetworkConditionProtocolCriteria(
            NetworkConditionProtocolId.GRAPHQL_HTTP,
            "{\"version\":1,\"operationName\":\"LoadFeed\"}",
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(
                rule("domain", "api.example.com", bypass),
                rule("operation", "api.example.com", slow).copy(protocolCriteria = graphQL),
            ),
        )

        val semantic = NetworkConditionMatcher.resolve(configuration, "api.example.com", 443) { it == graphQL }
        val opaque = NetworkConditionMatcher.resolve(configuration, "api.example.com", 443)

        assertEquals("operation", semantic?.ruleId?.value)
        assertEquals(EffectiveNetworkCondition.Source.PROTOCOL_RULE, semantic?.source)
        assertEquals("domain", opaque?.ruleId?.value)
        assertEquals(EffectiveNetworkCondition.Source.DOMAIN_RULE, opaque?.source)
    }

    @Test
    fun `explicit priority is evaluated before semantic and destination specificity`() {
        val graphQL = NetworkConditionProtocolCriteria(NetworkConditionProtocolId.GRAPHQL_HTTP, "valid")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(
                rule("semantic", "api.example.com", slow).copy(protocolCriteria = graphQL),
                rule("priority", "*.example.com", bypass).copy(priority = 50),
            ),
        )

        assertEquals(
            "priority",
            NetworkConditionMatcher.resolve(configuration, "api.example.com", 443) { true }?.ruleId?.value,
        )
    }

    @Test
    fun `invalid or unavailable semantic match falls through without broadening the rule`() {
        val graphQL = NetworkConditionProtocolCriteria(NetworkConditionProtocolId.GRAPHQL_HTTP, "invalid")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.HIGH_LATENCY.id,
            rules = listOf(rule("semantic", "api.example.com", slow).copy(protocolCriteria = graphQL)),
        )

        val effective = NetworkConditionMatcher.resolve(configuration, "api.example.com", 443) { false }

        assertEquals(EffectiveNetworkCondition.Source.GLOBAL, effective?.source)
        assertEquals(NetworkConditionBuiltIns.HIGH_LATENCY.id, effective?.profile?.id)
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
