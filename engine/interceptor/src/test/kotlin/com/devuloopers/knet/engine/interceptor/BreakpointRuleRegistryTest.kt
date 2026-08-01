package com.devuloopers.knet.engine.interceptor

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreakpointRuleRegistryTest {

    @BeforeTest
    fun setUp() {
        BreakpointRuleRegistry.clearRules()
    }

    @Test
    fun testRuleRegistrationAndPrioritySorting() {
        val r1 = BreakpointRule("b1", priority = 10)
        val r2 = BreakpointRule("b2", priority = 1)

        BreakpointRuleRegistry.addRule(r1)
        BreakpointRuleRegistry.addRule(r2)

        val rules = BreakpointRuleRegistry.getRules()
        assertEquals(2, rules.size)
        assertEquals("b2", rules.first().id, "Rule with lower priority integer must come first")
    }

    @Test
    fun testRuleRemovalAndClear() {
        BreakpointRuleRegistry.addRule(BreakpointRule("b1"))
        BreakpointRuleRegistry.addRule(BreakpointRule("b2"))

        BreakpointRuleRegistry.removeRule("b1")
        assertEquals(1, BreakpointRuleRegistry.getRules().size)
        assertEquals("b2", BreakpointRuleRegistry.getRules().first().id)

        BreakpointRuleRegistry.clearRules()
        assertTrue(BreakpointRuleRegistry.getRules().isEmpty())
    }
}
