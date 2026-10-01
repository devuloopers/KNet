package com.devuloopers.knet.domain.protectedtraffic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Deterministic policy, normalization, and compatibility-group coverage. */
class ProtectedTrafficPolicyTest {
    private val policy = ProtectedTrafficPolicy()

    @Test
    fun `application rule wins over destination block`() {
        val applicationRule = rule(
            id = "application",
            action = ProtectedTrafficAction.TUNNEL,
            application = "com.android.vending",
        )
        val destinationRule = rule(
            id = "destination",
            action = ProtectedTrafficAction.BLOCK,
            destination = "billing.example",
            port = 443,
        )

        val result = policy.evaluate(
            ProtectedTrafficConfiguration(rules = listOf(destinationRule, applicationRule)),
            request(application = "com.android.vending", host = "BILLING.EXAMPLE."),
        )

        assertEquals(ProtectedTrafficAction.TUNNEL, result.action)
        assertEquals(
            ProtectedTrafficRuleId("application"),
            assertIs<ProtectedTrafficDecisionEvidence.Rule>(result.evidence).ruleId,
        )
    }

    @Test
    fun `application plus exact destination and port is most specific`() {
        val applicationRule = rule(
            id = "application",
            action = ProtectedTrafficAction.TUNNEL,
            application = "com.example.player",
        )
        val combinedRule = rule(
            id = "combined",
            action = ProtectedTrafficAction.INSPECT,
            application = "com.example.player",
            destination = "api.example",
            port = 443,
        )

        val result = policy.evaluate(
            ProtectedTrafficConfiguration(rules = listOf(applicationRule, combinedRule)),
            request(application = "com.example.player", host = "api.example"),
        )

        assertEquals(ProtectedTrafficAction.INSPECT, result.action)
        assertEquals(
            ProtectedTrafficRuleId("combined"),
            assertIs<ProtectedTrafficDecisionEvidence.Rule>(result.evidence).ruleId,
        )
    }

    @Test
    fun `application plus destination remains more specific without a port`() {
        val applicationRule = rule(
            id = "application",
            action = ProtectedTrafficAction.TUNNEL,
            application = "com.example.player",
        )
        val combinedRule = rule(
            id = "combined",
            action = ProtectedTrafficAction.BLOCK,
            application = "com.example.player",
            destination = "api.example",
        )

        val result = policy.evaluate(
            ProtectedTrafficConfiguration(rules = listOf(applicationRule, combinedRule)),
            request(application = "com.example.player", host = "api.example"),
        )

        assertEquals(ProtectedTrafficAction.BLOCK, result.action)
        assertEquals(
            ProtectedTrafficRuleId("combined"),
            assertIs<ProtectedTrafficDecisionEvidence.Rule>(result.evidence).ruleId,
        )
    }

    @Test
    fun `application wildcard and port beats an application-only rule`() {
        val applicationRule = rule(
            id = "application",
            action = ProtectedTrafficAction.TUNNEL,
            application = "com.example.player",
        )
        val scopedRule = rule(
            id = "scoped",
            action = ProtectedTrafficAction.BLOCK,
            application = "com.example.player",
            destination = "*.service.example",
            port = 443,
        )

        val result = policy.evaluate(
            ProtectedTrafficConfiguration(rules = listOf(applicationRule, scopedRule)),
            request(application = "com.example.player", host = "api.service.example"),
        )

        assertEquals(ProtectedTrafficAction.BLOCK, result.action)
    }

    @Test
    fun `exact host wins over wildcard regardless of action`() {
        val wildcard = rule(
            id = "wildcard",
            action = ProtectedTrafficAction.BLOCK,
            destination = "*.example.com",
        )
        val exact = rule(
            id = "exact",
            action = ProtectedTrafficAction.INSPECT,
            destination = "store.example.com",
        )

        val result = policy.evaluate(
            ProtectedTrafficConfiguration(rules = listOf(wildcard, exact)),
            request(host = "store.example.com"),
        )

        assertEquals(ProtectedTrafficAction.INSPECT, result.action)
    }

    @Test
    fun `wildcard requires a child label`() {
        val wildcard = rule(
            id = "wildcard",
            action = ProtectedTrafficAction.TUNNEL,
            destination = "*.example.com",
        )
        val configuration = ProtectedTrafficConfiguration(rules = listOf(wildcard))

        assertEquals(
            ProtectedTrafficAction.INSPECT,
            policy.evaluate(configuration, request(host = "example.com")).action,
        )
        assertEquals(
            ProtectedTrafficAction.TUNNEL,
            policy.evaluate(configuration, request(host = "media.example.com")).action,
        )
    }

    @Test
    fun `IPv4 and IPv6 CIDR matching is canonical`() {
        val ipv4 = rule("ipv4", ProtectedTrafficAction.TUNNEL, destination = "10.9.8.7/8")
        val ipv6 = rule("ipv6", ProtectedTrafficAction.BLOCK, destination = "2001:db8:1::9/48")
        val configuration = ProtectedTrafficConfiguration(rules = listOf(ipv4, ipv6))

        assertEquals(
            ProtectedTrafficAction.TUNNEL,
            policy.evaluate(configuration, request(address = "10.200.1.2")).action,
        )
        assertEquals(
            ProtectedTrafficAction.BLOCK,
            policy.evaluate(configuration, request(address = "2001:db8:1::beef")).action,
        )
        assertEquals(
            ProtectedTrafficAction.INSPECT,
            policy.evaluate(configuration, request(address = "2001:db9::1")).action,
        )
    }

    @Test
    fun `disabled and non-enabled built-in groups do not participate`() {
        val configuration = ProtectedTrafficConfiguration(
            rules = ProtectedTrafficBuiltIns.rules,
        )

        assertEquals(
            ProtectedTrafficAction.INSPECT,
            policy.evaluate(configuration, request(application = "com.android.vending")).action,
        )
        val enabled = configuration.copy(
            enabledBuiltInGroups = setOf(ProtectedTrafficBuiltIns.GooglePlayBillingGroupId),
        )
        val result = policy.evaluate(enabled, request(application = "com.android.vending"))
        assertEquals(ProtectedTrafficAction.TUNNEL, result.action)
        assertEquals(
            ProtectedTrafficBuiltIns.GooglePlayBillingGroupId,
            assertIs<ProtectedTrafficDecisionEvidence.Rule>(result.evidence).groupId,
        )
    }

    @Test
    fun `user rule overrides an enabled built-in application rule`() {
        val user = rule(
            id = "inspect-play",
            action = ProtectedTrafficAction.INSPECT,
            application = "com.android.vending",
        )
        val configuration = ProtectedTrafficConfiguration(
            rules = ProtectedTrafficBuiltIns.rules + user,
            enabledBuiltInGroups = setOf(ProtectedTrafficBuiltIns.GooglePlayBillingGroupId),
        )

        val result = policy.evaluate(configuration, request(application = "com.android.vending"))

        assertEquals(ProtectedTrafficAction.INSPECT, result.action)
        assertEquals(
            ProtectedTrafficRuleId("inspect-play"),
            assertIs<ProtectedTrafficDecisionEvidence.Rule>(result.evidence).ruleId,
        )
    }

    @Test
    fun `equivalent normalized user selectors are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ProtectedTrafficConfiguration(
                rules = listOf(
                    rule("one", ProtectedTrafficAction.TUNNEL, destination = "API.EXAMPLE."),
                    rule("two", ProtectedTrafficAction.BLOCK, destination = "api.example"),
                ),
            )
        }
    }

    @Test
    fun `global default returns explicit evidence`() {
        val result = policy.evaluate(
            ProtectedTrafficConfiguration(defaultAction = ProtectedTrafficAction.BYPASS),
            request(host = "unmatched.example"),
        )

        assertEquals(ProtectedTrafficAction.BYPASS, result.action)
        assertIs<ProtectedTrafficDecisionEvidence.GlobalDefault>(result.evidence)
    }

    private fun rule(
        id: String,
        action: ProtectedTrafficAction,
        application: String? = null,
        destination: String? = null,
        port: Int? = null,
    ): ProtectedTrafficRule = ProtectedTrafficRule(
        id = ProtectedTrafficRuleId(id),
        action = action,
        sourceApplication = application?.let(::ProtectedSourceApplicationId),
        destination = destination?.let(ProtectedDestinationSelector::parse),
        port = port,
    )

    private fun request(
        application: String? = null,
        host: String? = null,
        address: String? = null,
    ): ProtectedTrafficRequest = ProtectedTrafficRequest(
        sourceApplication = application?.let(::ProtectedSourceApplicationId),
        destinationHost = host ?: if (address == null) "service.invalid" else null,
        destinationAddress = address,
        port = 443,
        transport = ProtectedTransportProtocol.TCP,
    )
}
