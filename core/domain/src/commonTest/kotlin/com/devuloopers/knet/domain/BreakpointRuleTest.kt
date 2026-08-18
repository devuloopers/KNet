package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.RulesUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakpointRuleTest {

    @Test
    fun testBreakpointRuleDefaults() {
        val rule = BreakpointRule(
            id = "rule-1",
            name = "Rewrite Auth Header",
            phase = BreakpointPhase.REQUEST,
            urlPattern = "*/api/*",
        )

        assertEquals("rule-1", rule.id)
        assertEquals("Rewrite Auth Header", rule.name)
        assertEquals(BreakpointPhase.REQUEST, rule.phase)
        assertEquals("*/api/*", rule.urlPattern)
        assertEquals(null, rule.method)
        assertTrue(rule.enabled)
    }

    @Test
    fun testBreakpointRuleCopyAndToggleState() {
        val original = TestFixtures.createBreakpointRule(id = "r-1", enabled = true)
        val disabled = original.copy(enabled = false)

        assertTrue(original.enabled)
        assertFalse(disabled.enabled)
        assertEquals("r-1", disabled.id)
    }

    @Test
    fun testRulesUiStateSealedClassVariants() {
        val loadingState: RulesUiState = RulesUiState.Loading
        assertTrue(loadingState is RulesUiState.Loading)

        val rule = TestFixtures.createBreakpointRule()
        val successState: RulesUiState = RulesUiState.Success(
            rules = listOf(rule)
        )

        if (successState is RulesUiState.Success) {
            assertEquals(1, successState.rules.size)
        }
    }

}
