package com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.usecase.protectedtraffic.DeleteProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.ObserveProtectedTrafficConfigurationUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedServiceGroupEnabledUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedTrafficDefaultActionUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.UpsertProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedTrafficFlowActionUseCase
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedServiceGroupId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficBuiltIns
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedTrafficRuleDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.OpaqueFlowSnapshot
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

        viewModel.selectDefaultAction(ProtectedTrafficAction.TUNNEL)
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
    fun `workspace exposes invalid selector errors and compatibility toggles`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.beginAddRule()
        viewModel.updateRuleDraft(ProtectedTrafficRuleDraft(destination = "*.invalid", port = "443"))
        viewModel.saveRuleDraft()
        viewModel.setCompatibilityGroupEnabled(ProtectedTrafficBuiltIns.GooglePlayBillingGroupId, true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.errorMessage.orEmpty().isNotBlank())
        assertTrue(
            ProtectedTrafficBuiltIns.GooglePlayBillingGroupId in
                repository.configuration.value.enabledBuiltInGroups,
        )
    }

    private fun viewModel(repository: ProtectedTrafficRepository) = ProtectedTrafficViewModel(
        ObserveProtectedTrafficConfigurationUseCase(repository),
        SetProtectedTrafficDefaultActionUseCase(repository),
        UpsertProtectedTrafficRuleUseCase(repository),
        DeleteProtectedTrafficRuleUseCase(repository),
        SetProtectedServiceGroupEnabledUseCase(repository),
        SetProtectedTrafficFlowActionUseCase(EmptyTrafficQuery, repository),
    )

    private object EmptyTrafficQuery : TrafficQuery {
        override val generations: Flow<TrafficGeneration> = emptyFlow()
        override suspend fun query(query: TrafficPageQuery): TrafficPage = TrafficPage(
            items = emptyList(),
            nextCursor = null,
            totalCount = 0L,
            generation = 0L,
        )
        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? = null
        override suspend fun getOpaqueFlow(flowId: OpaqueFlowId): OpaqueFlowSnapshot? = null
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            BodyChunk(byteArrayOf(), range.offset, endOfBody = true)
    }

    private class FakeRepository : ProtectedTrafficRepository {
        override val configuration = MutableStateFlow(ProtectedTrafficConfiguration())

        override suspend fun setDefaultAction(action: ProtectedTrafficAction) {
            configuration.value = configuration.value.copy(defaultAction = action)
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

        override suspend fun setBuiltInGroupEnabled(groupId: ProtectedServiceGroupId, enabled: Boolean) {
            configuration.value = configuration.value.copy(
                enabledBuiltInGroups = configuration.value.enabledBuiltInGroups.toMutableSet().apply {
                    if (enabled) add(groupId) else remove(groupId)
                },
            )
        }
    }
}
