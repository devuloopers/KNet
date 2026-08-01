package com.devuloopers.knet.engine.traffic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModifierRuleTest {

    @Test
    fun testModifierRuleCreationAndPriority() {
        val rule1 = ModifierRule(
            id = "r1", name = "Low Priority Rule",
            urlPattern = ".*", target = RuleTarget.REQUEST_HEADER,
            action = RuleAction.ADD, matchValue = "X-Low", newValue = "1",
            priority = 10
        )
        val rule2 = ModifierRule(
            id = "r2", name = "High Priority Rule",
            urlPattern = ".*", target = RuleTarget.REQUEST_HEADER,
            action = RuleAction.ADD, matchValue = "X-High", newValue = "2",
            priority = 0
        )

        val rules = listOf(rule1, rule2).sortedBy { it.priority }
        assertEquals("r2", rules.first().id, "Rule with lower priority integer must execute first")
        assertEquals(0, rules.first().priority)
    }

    @Test
    fun testMapLocalRuleDefaults() {
        val rule = MapLocalRule(
            id = "ml1", name = "Local Mock",
            urlPattern = ".*", localFilePath = "/tmp/test.json"
        )
        assertTrue(rule.enabled)
        assertEquals(0, rule.priority)
    }

    @Test
    fun testMapRemoteRuleDefaults() {
        val rule = MapRemoteRule(
            id = "mr1", name = "Remote Redirect",
            urlPattern = ".*", targetHost = "staging.com", targetPort = 8443
        )
        assertTrue(rule.enabled)
        assertEquals("https", rule.targetProtocol)
        assertEquals(0, rule.priority)
    }
}
