package com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.usecase.protectedtraffic.DeleteProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.ObserveProtectedTrafficConfigurationUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedTrafficDefaultActionUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.UpsertProtectedTrafficRuleUseCase
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedServiceGroupId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficDefaultHandling
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedTrafficRuleDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProtectedTrafficViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp(): Unit = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown(): Unit = Dispatchers.resetMain()

    @Test
    fun `workspace persists normalized application destination and global actions`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.selectDefaultHandling(ProtectedTrafficDefaultHandling.TUNNEL)
        viewModel.beginAddRule()
        viewModel.updateRuleDraft(
            ProtectedTrafficRuleDraft(
                sourceApplication = "com.example.player",
                destination = "*.Video.Example.Test.",
                port = "443",
                action = ProtectedTrafficAction.TUNNEL,
            ),
        )
        viewModel.saveRuleDraft()
        advanceUntilIdle()

        assertEquals(ProtectedTrafficAction.TUNNEL, repository.configuration.value.defaultAction)
        val rule = repository.configuration.value.rules.single()
        assertEquals(ProtectedSourceApplicationId("com.example.player"), rule.sourceApplication)
        assertEquals(
            ProtectedDestinationSelector.WildcardDomain("video.example.test"),
            rule.destination,
        )
        assertEquals(443, rule.port)
        assertNull(viewModel.state.value.ruleDraft)
    }

    @Test
    fun `workspace exposes invalid selector errors without preset controls`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.beginAddRule()
        viewModel.updateRuleDraft(ProtectedTrafficRuleDraft(destination = "*.invalid", port = "443"))
        viewModel.saveRuleDraft()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.draftError.orEmpty().isNotBlank())
        assertTrue(repository.configuration.value.enabledBuiltInGroups.isEmpty())
    }

    @Test
    fun `changing default policy preserves saved rules and an open draft`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val rule = ProtectedTrafficRule(ProtectedTrafficRuleId("saved"), action = ProtectedTrafficAction.TUNNEL,
            destination = ProtectedDestinationSelector.parse("api.example.com"))
        repository.configuration.value = ProtectedTrafficConfiguration(defaultAction = ProtectedTrafficAction.TUNNEL, rules = listOf(rule))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.beginAddRule()
        val draft = viewModel.state.value.ruleDraft
        viewModel.selectDefaultHandling(ProtectedTrafficDefaultHandling.BLOCK)
        advanceUntilIdle()
        assertEquals(ProtectedTrafficAction.BLOCK, repository.configuration.value.defaultAction)
        assertEquals(listOf(rule), repository.configuration.value.rules)
        assertEquals(draft, viewModel.state.value.ruleDraft)
        assertEquals(ProtectedTrafficAction.BLOCK, viewModel.state.value.configuration.defaultAction)
    }

    @Test
    fun `editing disabled rule retains identity and enabled state`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val rule = ProtectedTrafficRule(ProtectedTrafficRuleId("edit-me"), enabled = false,
            action = ProtectedTrafficAction.TUNNEL, destination = ProtectedDestinationSelector.parse("old.example.com"))
        repository.upsertRule(rule)
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.beginEditRule(rule)
        viewModel.updateRuleDraft(viewModel.state.value.ruleDraft!!.copy(destination = "new.example.com"))
        viewModel.saveRuleDraft()
        advanceUntilIdle()
        val saved = repository.configuration.value.rules.single()
        assertEquals(rule.id, saved.id)
        assertEquals(false, saved.enabled)
        assertEquals(ProtectedDestinationSelector.parse("new.example.com"), saved.destination)
    }

    private fun viewModel(repository: ProtectedTrafficRepository) = ProtectedTrafficViewModel(
        ObserveProtectedTrafficConfigurationUseCase(repository), SetProtectedTrafficDefaultActionUseCase(repository),
        UpsertProtectedTrafficRuleUseCase(repository), DeleteProtectedTrafficRuleUseCase(repository),
    )

    private class FakeRepository : ProtectedTrafficRepository {
        override val configuration = MutableStateFlow(ProtectedTrafficConfiguration())


        override suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean) {
            configuration.value = configuration.value.copy(defaultAction = action, automaticTlsFallbackEnabled = automaticFallbackEnabled)
        }

        override suspend fun upsertRule(rule: ProtectedTrafficRule) {
            configuration.value = configuration.value.copy(
                rules = configuration.value.rules.filterNot { it.id == rule.id } + rule,
            )
        }

        override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) {
            configuration.value = configuration.value.copy(
                rules = configuration.value.rules.filterNot { it.id == ruleId },
            )
        }


    }
}
