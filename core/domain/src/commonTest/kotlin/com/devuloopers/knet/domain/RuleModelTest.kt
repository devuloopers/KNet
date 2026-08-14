package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.RulesIntent
import com.devuloopers.knet.domain.rules.model.RulesUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleModelTest {

    @Test
    fun testRuleModelDefaults() {
        val rule = RuleModel(
            name = "Rewrite Auth Header",
            type = BreakpointPhase.REQUEST,
            condition = "url CONTAINS '/api/'",
            action = "Add Header"
        )

        assertEquals("", rule.id)
        assertEquals("Rewrite Auth Header", rule.name)
        assertEquals(BreakpointPhase.REQUEST, rule.type)
        assertEquals("url CONTAINS '/api/'", rule.condition)
        assertEquals("Add Header", rule.action)
        assertTrue(rule.enabled)
    }

    @Test
    fun testRuleModelCopyAndToggleState() {
        val original = TestFixtures.createRuleModel(id = "r-1", enabled = true)
        val disabled = original.copy(enabled = false)

        assertTrue(original.enabled)
        assertFalse(disabled.enabled)
        assertEquals("r-1", disabled.id)
    }

    @Test
    fun testRulesUiStateSealedClassVariants() {
        val loadingState: RulesUiState = RulesUiState.Loading
        assertTrue(loadingState is RulesUiState.Loading)

        val rule = TestFixtures.createRuleModel()
        val successState: RulesUiState = RulesUiState.Success(
            rules = listOf(rule)
        )

        if (successState is RulesUiState.Success) {
            assertEquals(1, successState.rules.size)
        }
    }

    @Test
    fun testRulesIntentVariants() {
        val rule = TestFixtures.createRuleModel(id = "rule-100")
        val toggleIntent = RulesIntent.ToggleRule(ruleId = "rule-100", enabled = false)
        assertEquals("rule-100", toggleIntent.ruleId)
        assertFalse(toggleIntent.enabled)

        val saveIntent = RulesIntent.SaveRule(rule)
        assertEquals("rule-100", saveIntent.rule.id)

        val deleteIntent = RulesIntent.DeleteRule("rule-100")
        assertEquals("rule-100", deleteIntent.ruleId)
    }
}
