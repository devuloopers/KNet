package com.devuloopers.knet.application.usecase.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedServiceGroupId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.OpaqueFlowSnapshot
import com.devuloopers.knet.traffic.model.OpaqueFlowState
import com.devuloopers.knet.traffic.model.OpaqueSecurityProtocol
import com.devuloopers.knet.traffic.model.OpaqueTransportProtocol
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SetProtectedTrafficFlowActionUseCaseTest {
    @Test
    fun `destination action updates the equivalent rule without duplicating it`() = runTest {
        val existing = ProtectedTrafficRule(
            id = ProtectedTrafficRuleId("existing"),
            action = ProtectedTrafficAction.INSPECT,
            destination = ProtectedDestinationSelector.parse("video.example.test"),
            port = 443,
            transport = com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol.TCP,
        )
        val repository = FakeRepository(ProtectedTrafficConfiguration(rules = listOf(existing)))
        val useCase = SetProtectedTrafficFlowActionUseCase(
            FakeTrafficQuery(flow(sourceApplicationId = "com.example.streaming")),
            repository,
        )

        val saved = assertIs<SetProtectedTrafficFlowActionResult.Saved>(
            useCase.execute(FLOW_ID, ProtectedTrafficAction.TUNNEL, ProtectedTrafficQuickRuleScope.DESTINATION),
        ).rule

        assertEquals(existing.id, saved.id)
        assertEquals(ProtectedTrafficAction.TUNNEL, saved.action)
        assertEquals(listOf(saved), repository.configuration.value.rules)
    }

    @Test
    fun `application action fails closed when source identity is unavailable`() = runTest {
        val repository = FakeRepository(ProtectedTrafficConfiguration())
        val useCase = SetProtectedTrafficFlowActionUseCase(FakeTrafficQuery(flow(null)), repository)

        val result = useCase.execute(
            FLOW_ID,
            ProtectedTrafficAction.TUNNEL,
            ProtectedTrafficQuickRuleScope.SOURCE_APPLICATION,
        )

        assertIs<SetProtectedTrafficFlowActionResult.SourceApplicationUnavailable>(result)
        assertEquals(emptyList(), repository.configuration.value.rules)
    }

    private fun flow(sourceApplicationId: String?): OpaqueFlowSnapshot = OpaqueFlowSnapshot(
        id = FLOW_ID,
        connectionId = ConnectionId("connection"),
        destination = TrafficEndpoint("203.0.113.10", 443),
        serverName = "Video.Example.Test",
        transport = OpaqueTransportProtocol.TCP,
        security = OpaqueSecurityProtocol.TLS,
        sourceApplicationId = sourceApplicationId,
        policyRuleId = null,
        startedAtEpochMillis = 1L,
        completedAtEpochMillis = 2L,
        uploadedBytes = 10L,
        downloadedBytes = 20L,
        state = OpaqueFlowState.COMPLETED,
        terminalOutcome = ExchangeTerminalOutcome.Completed,
    )

    private class FakeTrafficQuery(private val flow: OpaqueFlowSnapshot) : TrafficQuery {
        override val generations: Flow<TrafficGeneration> = emptyFlow()
        override suspend fun query(query: TrafficPageQuery): TrafficPage =
            TrafficPage(items = emptyList(), nextCursor = null, totalCount = 0L, generation = 0L)
        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? = null
        override suspend fun getOpaqueFlow(flowId: OpaqueFlowId): OpaqueFlowSnapshot? =
            flow.takeIf { it.id == flowId }
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            BodyChunk(byteArrayOf(), range.offset, true)
    }

    private class FakeRepository(initial: ProtectedTrafficConfiguration) : ProtectedTrafficRepository {
        override val configuration = MutableStateFlow(initial)
        override suspend fun setDefaultAction(action: ProtectedTrafficAction) = Unit
        override suspend fun upsertRule(rule: ProtectedTrafficRule) {
            configuration.value = configuration.value.copy(
                rules = configuration.value.rules.filterNot { it.id == rule.id } + rule,
            )
        }
        override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) = Unit
        override suspend fun setBuiltInGroupEnabled(groupId: ProtectedServiceGroupId, enabled: Boolean) = Unit
    }

    private companion object {
        val FLOW_ID: OpaqueFlowId = OpaqueFlowId("flow")
    }
}
