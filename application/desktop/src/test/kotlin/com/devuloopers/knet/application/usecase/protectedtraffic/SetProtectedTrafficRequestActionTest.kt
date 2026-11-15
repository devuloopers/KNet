package com.devuloopers.knet.application.usecase.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Request-row quick actions resolve canonical authorities and original device scope, not mutable UI state. */
class SetProtectedTrafficRequestActionTest {
    @Test fun `all three actions from an ordinary request replace one exact private destination rule`() = runTest {
        val fixture = Fixture()
        fixture.query.record = exchange(RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("API.Example.Test", 8443), "/graphql"))
        val actions = listOf(ProtectedTrafficAction.TUNNEL, ProtectedTrafficAction.INSPECT, ProtectedTrafficAction.BLOCK)
        var id: ProtectedTrafficRuleId? = null
        for (action in actions) {
            val saved = assertIs<SetProtectedTrafficFlowActionResult.Saved>(fixture.useCase.execute(fixture.id, action)).rule
            assertEquals(ProtectedDestinationSelector.parse("api.example.test"), saved.destination)
            assertEquals(8443, saved.port); assertEquals(ProtectedTransportProtocol.TCP, saved.transport)
            assertEquals(action, saved.action)
            if (id == null) id = saved.id else assertEquals(id, saved.id)
            assertEquals(listOf(saved), fixture.repository.configuration.value.rules)
        }
        assertEquals(ExchangeState.COMPLETED, fixture.query.record!!.state)
    }

    @Test fun `scheme defaults explicit ports and IPv6 are retained without inventing unknown authorities`() = runTest {
        val fixture = Fixture()
        for ((scheme, expected) in listOf("https" to 443, "http" to 80, "wss" to 443, "ws" to 80)) {
            fixture.query.record = exchange(RequestTarget.Absolute(HttpScheme.fromToken(scheme), Authority("2001:db8::1"), "/"))
            val rule = assertIs<SetProtectedTrafficFlowActionResult.Saved>(fixture.useCase.execute(fixture.id, ProtectedTrafficAction.BLOCK)).rule
            assertEquals(expected, rule.port); assertEquals(ProtectedDestinationSelector.parse("2001:db8::1"), rule.destination)
        }
        for (target in listOf(RequestTarget.Origin("/graphql"), RequestTarget.AuthorityForm(Authority("api.test")),
            RequestTarget.Absolute(HttpScheme.fromToken("custom"), Authority("api.test"), "/"))) {
            fixture.query.record = exchange(target)
            val before = fixture.repository.configuration.value
            assertIs<SetProtectedTrafficFlowActionResult.DestinationUnavailable>(fixture.useCase.execute(fixture.id, ProtectedTrafficAction.TUNNEL))
            assertEquals(before, fixture.repository.configuration.value)
        }
    }

    @Test fun `captured device determines ownership even with identical host and record text across kinds`() = runTest {
        val fixture = Fixture()
        val a = PolicyWorkspace.Device(RegisteredDeviceId("device-a")); val b = PolicyWorkspace.Device(RegisteredDeviceId("device-b"))
        fixture.query.record = exchange(RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("api.test"), "/"))
        fixture.query.context = TrafficConnectionIngress(IngressContext(IngressKind.LanPairedDevice, ClientIdentity("device-a")), null)
        fixture.useCase.execute(fixture.id, ProtectedTrafficAction.TUNNEL)
        assertEquals(1, fixture.repository.forWorkspace(a).configuration.value.rules.size)
        assertTrue(fixture.repository.forWorkspace(b).configuration.value.rules.isEmpty())
        assertTrue(fixture.repository.configuration.value.rules.isEmpty())
        fixture.query.record = null
        assertIs<SetProtectedTrafficFlowActionResult.MissingFlow>(fixture.useCase.execute(fixture.id, ProtectedTrafficAction.BLOCK))
        assertEquals(ProtectedTrafficAction.TUNNEL, fixture.repository.forWorkspace(a).configuration.value.rules.single().action)
    }

    private class Fixture {
        val id = TrafficRecordId.Exchange(ExchangeId("request"))
        val query = Query()
        val repository = Repository()
        val useCase = SetProtectedTrafficFlowActionUseCase(query, repository)
    }
    private class Query : TrafficQuery {
        var record: HttpExchangeSnapshot? = null
        var context: TrafficConnectionIngress? = null
        override val generations = emptyFlow<TrafficGeneration>()
        override suspend fun query(query: TrafficPageQuery) = TrafficPage(items = emptyList(), nextCursor = null, totalCount = 0, generation = 0)
        override suspend fun getExchange(exchangeId: ExchangeId) = record?.takeIf { it.id == exchangeId }
        override suspend fun getConnectionIngress(connectionId: ConnectionId) = context
        override suspend fun readBody(bodyId: BodyId, range: BodyRange) = error("Quick actions never read bodies")
    }
    private class Repository(private val workspace: PolicyWorkspace = PolicyWorkspace.Other,
        private val states: MutableMap<PolicyWorkspace, MutableStateFlow<ProtectedTrafficConfiguration>> = mutableMapOf()) : ProtectedTrafficRepository {
        override val configuration = states.getOrPut(workspace) { MutableStateFlow(ProtectedTrafficConfiguration()) }
        override fun forWorkspace(workspace: PolicyWorkspace) = Repository(workspace, states)
        override fun observeCatalog() = configuration.map { config -> ProtectionPolicyCatalog(workspace, "Test", config,
            config.rules.map { PolicyDefinition(it, RuleAvailability.Private(workspace), 1, setOf(workspace)) }) }
        override suspend fun saveDefinition(rule: ProtectedTrafficRule, context: RuleEditContext): RuleSaveResult {
            assertEquals(workspace, context.workspace); assertEquals(RuleAvailability.Private(workspace), context.availability)
            upsertRule(rule); return RuleSaveResult()
        }
        override suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean) = Unit
        override suspend fun upsertRule(rule: ProtectedTrafficRule) { configuration.value = configuration.value.copy(rules = configuration.value.rules.filterNot { it.id == rule.id } + rule) }
        override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) = Unit
    }
    private companion object {
        fun exchange(target: RequestTarget) = HttpExchangeSnapshot(ExchangeId("request"), ConnectionId("connection"),
            request = HttpRequestSnapshot(RequestHead(HttpMethod.fromToken("POST"), target, ApplicationProtocol.fromToken("h2"), emptyList())),
            state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
    }
}
