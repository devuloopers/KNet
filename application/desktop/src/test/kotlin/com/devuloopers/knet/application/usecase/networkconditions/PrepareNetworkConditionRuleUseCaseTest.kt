package com.devuloopers.knet.application.usecase.networkconditions

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.OpaqueFlowSnapshot
import com.devuloopers.knet.traffic.model.OpaqueFlowState
import com.devuloopers.knet.traffic.model.OpaqueSecurityProtocol
import com.devuloopers.knet.traffic.model.OpaqueTransportProtocol
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PrepareNetworkConditionRuleUseCaseTest {
    @Test
    fun `traffic quick-add uses canonical authority and remembers the selected profile`() = runTest {
        val remembered = NetworkConditionBuiltIns.FAST_3G.id
        val repository = FakeRepository(
            NetworkConditionConfiguration(lastQuickAddProfileId = remembered),
        )
        val exchange = exchange(
            RequestTarget.Absolute(
                HttpScheme.fromToken("https"),
                Authority("Video.Example.Test.", null),
                "/manifest.m3u8?token=secret",
            ),
        )

        val result = assertIs<PrepareNetworkConditionRuleResult.Ready>(
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange), repository).execute(exchange.id),
        )

        assertEquals("video.example.test:443", result.target.displayValue)
        assertEquals(remembered, result.suggestedProfileId)
        assertEquals(null, result.existingRuleId)
    }

    @Test
    fun `traffic quick-add opens an equivalent existing rule instead of duplicating it`() = runTest {
        val target = NetworkConditionTarget.parse("api.example.test", 8443)
        val existing = NetworkConditionRule(
            NetworkConditionRuleId("existing"),
            target,
            NetworkConditionBuiltIns.SLOW_3G.id,
        )
        val repository = FakeRepository(NetworkConditionConfiguration(rules = listOf(existing)))
        val exchange = exchange(RequestTarget.AuthorityForm(Authority("API.EXAMPLE.TEST", 8443)))

        val result = assertIs<PrepareNetworkConditionRuleResult.Ready>(
            PrepareNetworkConditionRuleUseCase(FakeTrafficQuery(exchange), repository).execute(exchange.id),
        )

        assertEquals(existing.id, result.existingRuleId)
        assertEquals(existing.profileId, result.suggestedProfileId)
        assertEquals(target, result.target)
    }

    @Test
    fun `display-only relative targets are never reparsed as network authorities`() = runTest {
        val exchange = exchange(RequestTarget.Origin("/video.ts?host=wrong.example"))
        val result = PrepareNetworkConditionRuleUseCase(
            FakeTrafficQuery(exchange),
            FakeRepository(NetworkConditionConfiguration()),
        ).execute(exchange.id)

        assertIs<PrepareNetworkConditionRuleResult.DestinationUnavailable>(result)
    }

    @Test
    fun `missing traffic identifier is reported without opening an empty rule`() = runTest {
        val result = PrepareNetworkConditionRuleUseCase(
            FakeTrafficQuery(exchange(RequestTarget.Origin("/"))),
            FakeRepository(NetworkConditionConfiguration()),
        ).execute(ExchangeId("missing"))

        assertIs<PrepareNetworkConditionRuleResult.MissingExchange>(result)
    }

    @Test
    fun `opaque traffic uses trusted server name and destination port`() = runTest {
        val opaque = OpaqueFlowSnapshot(
            id = OpaqueFlowId("opaque"),
            connectionId = ConnectionId("connection"),
            destination = TrafficEndpoint("203.0.113.10", 443),
            serverName = "Video.Example.Test.",
            transport = OpaqueTransportProtocol.TCP,
            security = OpaqueSecurityProtocol.TLS,
            sourceApplicationId = null,
            policyRuleId = null,
            startedAtEpochMillis = 1L,
            completedAtEpochMillis = null,
            uploadedBytes = 0L,
            downloadedBytes = 0L,
            state = OpaqueFlowState.ACTIVE,
            terminalOutcome = null,
        )
        val result = assertIs<PrepareNetworkConditionRuleResult.Ready>(
            PrepareNetworkConditionRuleUseCase(
                FakeTrafficQuery(exchange = null, opaque = opaque),
                FakeRepository(NetworkConditionConfiguration()),
            ).execute(ExchangeId(opaque.id.value)),
        )

        assertEquals("video.example.test:443", result.target.displayValue)
    }

    private fun exchange(target: RequestTarget): HttpExchangeSnapshot = HttpExchangeSnapshot(
        id = ExchangeId("exchange"),
        request = HttpRequestSnapshot(
            head = RequestHead(
                method = HttpMethod.GET,
                target = target,
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = emptyList(),
            ),
        ),
        state = ExchangeState.COMPLETED,
        startedAtEpochMillis = 1L,
    )

    private class FakeTrafficQuery(
        private val exchange: HttpExchangeSnapshot?,
        private val opaque: OpaqueFlowSnapshot? = null,
    ) : TrafficQuery {
        override val generations: Flow<TrafficGeneration> = emptyFlow()
        override suspend fun query(query: TrafficPageQuery): TrafficPage =
            TrafficPage(items = emptyList(), nextCursor = null, totalCount = 0L, generation = 0L)
        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            exchange?.takeIf { it.id == exchangeId }
        override suspend fun getOpaqueFlow(flowId: OpaqueFlowId): OpaqueFlowSnapshot? =
            opaque?.takeIf { it.id == flowId }
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            BodyChunk(byteArrayOf(), 0L, true)
    }

    private class FakeRepository(initial: NetworkConditionConfiguration) : NetworkConditionsRepository {
        override val configuration = MutableStateFlow(initial)
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override suspend fun setGlobalProfile(profileId: NetworkConditionProfileId?) = Unit
        override suspend fun upsertProfile(profile: NetworkConditionProfile) = Unit
        override suspend fun deleteProfile(profileId: NetworkConditionProfileId): Boolean = true
        override suspend fun upsertRule(rule: NetworkConditionRule) = Unit
        override suspend fun deleteRule(ruleId: NetworkConditionRuleId) = Unit
        override suspend fun setLastQuickAddProfile(profileId: NetworkConditionProfileId?) = Unit
        override suspend fun resetShaping() = Unit
    }
}
