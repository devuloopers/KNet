package com.devuloopers.knet.engine.traffic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrafficModifierManagerTest {

    @Test
    fun testRuleRegistrationAndValidation() {
        val manager = TrafficModifierManager()

        val validRule = ModifierRule("r1", "Rule 1", ".*api.*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, "X-Tag", "v1")
        manager.addModifierRule(validRule)
        assertEquals(1, manager.getModifierRules().size)

        // Invalid regex validation
        assertFailsWith<IllegalArgumentException> {
            manager.addModifierRule(
                ModifierRule("r2", "Bad Regex", "[invalid", RuleTarget.REQUEST_HEADER, RuleAction.ADD)
            )
        }

        // Invalid port validation
        assertFailsWith<IllegalArgumentException> {
            manager.addMapRemoteRule(
                MapRemoteRule("mr1", "Bad Port", ".*", "host.com", 999999)
            )
        }
    }

    @Test
    fun testPrioritySortingInReadAccessors() {
        val manager = TrafficModifierManager()
        val r1 = ModifierRule("r1", "Low Priority", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, priority = 20)
        val r2 = ModifierRule("r2", "High Priority", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, priority = 5)

        manager.addModifierRule(r1)
        manager.addModifierRule(r2)

        val sorted = manager.getModifierRules()
        assertEquals("r2", sorted.first().id)
        assertEquals("r1", sorted.last().id)
    }

    @Test
    fun testClearAllRules() {
        val tempFile = TestFixtures.createTempFile()
        val manager = TrafficModifierManager()

        manager.addModifierRule(ModifierRule("r1", "Rule 1", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD))
        manager.addMapLocalRule(MapLocalRule("ml1", "Local 1", ".*", tempFile.absolutePath))
        manager.addMapRemoteRule(MapRemoteRule("mr1", "Remote 1", ".*", "target.com", 443))

        manager.clearAllRules()
        assertTrue(manager.getModifierRules().isEmpty())
        assertTrue(manager.getMapLocalRules().isEmpty())
        assertTrue(manager.getMapRemoteRules().isEmpty())
    }
}
