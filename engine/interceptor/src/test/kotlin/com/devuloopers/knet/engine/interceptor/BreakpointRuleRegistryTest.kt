package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.RuleModel
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
    fun testRuleRegistrationAndRetrieval() {
        val r1 = RuleModel(id = "b1", name = "b1", condition = "*", action = "GET")
        val r2 = RuleModel(id = "b2", name = "b2", condition = "*", action = "POST")

        BreakpointRuleRegistry.addRule(r1)
        BreakpointRuleRegistry.addRule(r2)

        val rules = BreakpointRuleRegistry.getRules()
        assertEquals(2, rules.size)
        assertTrue(rules.any { it.id == "b1" })
        assertTrue(rules.any { it.id == "b2" })
    }

    @Test
    fun testRuleRemovalAndClear() {
        BreakpointRuleRegistry.addRule(RuleModel(id = "b1", name = "b1", condition = "*", action = "GET"))
        BreakpointRuleRegistry.addRule(RuleModel(id = "b2", name = "b2", condition = "*", action = "POST"))

        BreakpointRuleRegistry.removeRule("b1")
        assertEquals(1, BreakpointRuleRegistry.getRules().size)
        assertEquals("b2", BreakpointRuleRegistry.getRules().first().id)

        BreakpointRuleRegistry.clearRules()
        assertTrue(BreakpointRuleRegistry.getRules().isEmpty())
    }
}
