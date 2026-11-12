package com.devuloopers.knet.ui.desktop.protectedtraffic.model

import com.devuloopers.knet.domain.protectedtraffic.*
import kotlin.test.*

/** Presentation filtering stays literal, includes disabled/imported rules and never reorders authoritative policy. */
class ProtectedRuleSearchTest {
    private val video = ProtectedTrafficRule(ProtectedTrafficRuleId("video"), action = ProtectedTrafficAction.TUNNEL,
        sourceApplication = ProtectedSourceApplicationId("com.example.player"), destination = ProtectedDestinationSelector.parse("*.video.example.com"), port = 443)
    private val ipv6 = ProtectedTrafficRule(ProtectedTrafficRuleId("ipv6"), action = ProtectedTrafficAction.BLOCK,
        destination = ProtectedDestinationSelector.parse("2001:db8::/32"), port = 8443, transport = ProtectedTransportProtocol.TCP,
        enabled = false, origin = ProtectedTrafficRuleOrigin.MIGRATED_PRESET)
    private val retired = ProtectedTrafficRule(ProtectedTrafficRuleId("retired"), action = ProtectedTrafficAction.TUNNEL,
        destination = ProtectedDestinationSelector.parse("old.example.com"), origin = ProtectedTrafficRuleOrigin.BUILT_IN,
        groupId = ProtectedServiceGroupId("old-group"), groupVersion = 1)
    private val configuration = ProtectedTrafficConfiguration(rules = listOf(ipv6, retired, video))

    @Test fun `case insensitive terms match independently across destination app port action and provenance`() {
        val cases = mapOf("pLaYeR 443 TuNnEl" to listOf(video), "*.video.example.com" to listOf(video),
            "2001: /32 BLOCK" to listOf(ipv6), "IMPORTED 8443" to listOf(ipv6), "[literal]" to emptyList())
        for ((query, expected) in cases) assertEquals(expected, ProtectedTrafficState(configuration = configuration, searchQuery = query).filteredRules)
    }

    @Test fun `clearing whitespace search restores the original non built in order including disabled rules`() {
        assertEquals(listOf(ipv6, video), ProtectedTrafficState(configuration = configuration, searchQuery = "  \t ").filteredRules)
        assertEquals(listOf(ipv6, retired, video), configuration.rules)
    }
}
