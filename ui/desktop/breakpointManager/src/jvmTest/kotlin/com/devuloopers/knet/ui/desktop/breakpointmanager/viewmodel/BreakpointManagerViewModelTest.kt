package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.RuleType
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.*
import com.devuloopers.knet.engine.interceptor.BreakpointPhase
import com.devuloopers.knet.engine.interceptor.BreakpointRuleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

private class FakeTestRulesRepository : RulesRepository {
    private val _rules = MutableStateFlow<List<RuleModel>>(emptyList())
    override val rulesFlow: Flow<List<RuleModel>> = _rules.asStateFlow()

    private val _isGlobalInterceptionEnabled = MutableStateFlow(true)
    override val isGlobalInterceptionEnabled: Flow<Boolean> = _isGlobalInterceptionEnabled.asStateFlow()

    override suspend fun toggleGlobalInterception(enabled: Boolean) {
        _isGlobalInterceptionEnabled.value = enabled
    }

    override suspend fun saveRule(rule: RuleModel) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        _rules.value = current
    }

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        val current = _rules.value.map {
            if (it.id == ruleId) it.copy(enabled = enabled) else it
        }
        _rules.value = current
    }

    override suspend fun deleteRule(ruleId: String) {
        _rules.value = _rules.value.filterNot { it.id == ruleId }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BreakpointManagerViewModelTest {

    private val repository = FakeTestRulesRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BreakpointRuleRegistry.clearRules()
        BreakpointRuleRegistry.toggleGlobalInterception(true)
    }

    @AfterTest
    fun tearDown() {
        BreakpointRuleRegistry.clearRules()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BreakpointManagerViewModel {
        return BreakpointManagerViewModel(
            getRulesUseCase = GetRulesUseCase(repository),
            observeGlobalInterceptionUseCase = ObserveGlobalInterceptionUseCase(repository),
            saveRuleUseCase = SaveRuleUseCase(repository),
            toggleRuleUseCase = ToggleRuleUseCase(repository),
            deleteRuleUseCase = DeleteRuleUseCase(repository),
            toggleGlobalInterceptionUseCase = ToggleGlobalInterceptionUseCase(repository)
        )
    }

    @Test
    fun `toggle global interception updates state correctly`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isGlobalInterceptionEnabled)

        viewModel.toggleGlobalInterception(false)
        assertFalse(viewModel.uiState.value.isGlobalInterceptionEnabled)
    }

    @Test
    fun `saveRule and toggleRuleStatus update domain registry and UI state`() = runTest {
        val viewModel = createViewModel()

        viewModel.saveRule(
            urlPattern = ".*stripe.*",
            method = HttpMethod.POST,
            phase = BreakpointPhase.REQUEST,
            enabled = true
        )

        assertEquals(1, viewModel.uiState.value.rules.size)
        val rule = viewModel.uiState.value.rules.first()
        assertEquals(".*stripe.*", rule.urlPattern)
        assertTrue(rule.enabled)

        viewModel.toggleRuleStatus(rule.id)
        val updated = viewModel.uiState.value.rules.first()
        assertFalse(updated.enabled)
    }

    @Test
    fun `search query filters rules by url pattern`() = runTest {
        val repositoryWithRules = FakeTestRulesRepository()
        repositoryWithRules.saveRule(
            RuleModel(
                id = "r1",
                name = ".*stripe.*",
                type = RuleType.REQUEST,
                condition = ".*stripe.*",
                action = "POST",
                enabled = true
            )
        )
        repositoryWithRules.saveRule(
            RuleModel(
                id = "r2",
                name = ".*auth/login.*",
                type = RuleType.BOTH,
                condition = ".*auth/login.*",
                action = "ALL",
                enabled = true
            )
        )

        val viewModel = BreakpointManagerViewModel(
            getRulesUseCase = GetRulesUseCase(repositoryWithRules),
            observeGlobalInterceptionUseCase = ObserveGlobalInterceptionUseCase(repositoryWithRules),
            saveRuleUseCase = SaveRuleUseCase(repositoryWithRules),
            toggleRuleUseCase = ToggleRuleUseCase(repositoryWithRules),
            deleteRuleUseCase = DeleteRuleUseCase(repositoryWithRules),
            toggleGlobalInterceptionUseCase = ToggleGlobalInterceptionUseCase(repositoryWithRules)
        )

        assertEquals(2, viewModel.uiState.value.rules.size)

        viewModel.updateSearchQuery("stripe")
        assertEquals(1, viewModel.uiState.value.filteredRules.size)
        assertEquals("r1", viewModel.uiState.value.filteredRules.first().id)
    }

    @Test
    fun `deleteRule removes target rule from registry`() = runTest {
        val repositoryWithRule = FakeTestRulesRepository()
        repositoryWithRule.saveRule(
            RuleModel(
                id = "r1",
                name = ".*stripe.*",
                type = RuleType.REQUEST,
                condition = ".*stripe.*",
                action = "POST",
                enabled = true
            )
        )

        val viewModel = BreakpointManagerViewModel(
            getRulesUseCase = GetRulesUseCase(repositoryWithRule),
            observeGlobalInterceptionUseCase = ObserveGlobalInterceptionUseCase(repositoryWithRule),
            saveRuleUseCase = SaveRuleUseCase(repositoryWithRule),
            toggleRuleUseCase = ToggleRuleUseCase(repositoryWithRule),
            deleteRuleUseCase = DeleteRuleUseCase(repositoryWithRule),
            toggleGlobalInterceptionUseCase = ToggleGlobalInterceptionUseCase(repositoryWithRule)
        )

        assertEquals(1, viewModel.uiState.value.rules.size)

        viewModel.deleteRule("r1")
        assertEquals(0, viewModel.uiState.value.rules.size)
    }
}
