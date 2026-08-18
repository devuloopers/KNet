package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.rules.model.RulesUiState
import com.devuloopers.knet.domain.rules.usecase.GetRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleRuleUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UseCaseTest {

    @Test
    fun testGetRulesUseCaseSuccessPath() = runTest {
        val repository = FakeRulesRepository()
        val rule1 = TestFixtures.createBreakpointRule(id = "r-1", name = "Rule One")
        val rule2 = TestFixtures.createBreakpointRule(id = "r-2", name = "Rule Two")
        repository.setRules(listOf(rule1, rule2))

        val useCase = GetRulesUseCase(repository)
        val state = useCase.execute().first()

        assertTrue(state is RulesUiState.Success)
        assertEquals(2, state.rules.size)
        assertEquals("Rule One", state.rules[0].name)
        assertEquals("Rule Two", state.rules[1].name)
    }

    @Test
    fun testToggleRuleUseCaseUpdatesRepository() = runTest {
        val repository = FakeRulesRepository()
        val rule = TestFixtures.createBreakpointRule(id = "rule-10", enabled = true)
        repository.setRules(listOf(rule))

        val toggleUseCase = ToggleRuleUseCase(repository)
        toggleUseCase.execute(ruleId = "rule-10", enabled = false)

        val updatedRules = repository.rulesFlow.first()
        assertEquals(1, updatedRules.size)
        assertFalse(updatedRules[0].enabled)
    }

    @Test
    fun testSaveRuleUseCaseInsertsNewRule() = runTest {
        val repository = FakeRulesRepository()
        val saveUseCase = SaveRuleUseCase(repository)

        val newRule = TestFixtures.createBreakpointRule(id = "rule-999", name = "New Map Remote")
        saveUseCase.execute(newRule)

        val currentRules = repository.rulesFlow.first()
        assertEquals(1, currentRules.size)
        assertEquals("rule-999", currentRules[0].id)
        assertEquals("New Map Remote", currentRules[0].name)
    }
}
