package com.devuloopers.knet.application.usecase.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.port.breakpoint.CompiledProtocolCriteria
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.ProtocolInspectionInput
import com.devuloopers.knet.application.port.breakpoint.ProtocolObservation
import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyRef
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PrepareBreakpointRuleDraftUseCaseTest {
    @Test
    fun `semantic suggestion initializes protocol values and removes volatile query parameters`() = runTest {
        val exchange = exchange(
            pathAndQuery = "/graphql?cacheKey=temporary",
            semanticValue = "GetProfile",
        )
        val useCase = useCase(exchange, SmartProtocolExtension())

        val result = useCase.execute(exchange.id)

        val draft = assertIs<PrepareBreakpointRuleDraftResult.Found>(result).draft
        assertEquals(SMART_PROTOCOL_ID, draft.rule.protocolCriteria.protocolId)
        assertEquals("GetProfile", draft.protocolValues.single().value)
        assertEquals("https://api.example.test:443/graphql", draft.rule.urlPattern)
        assertEquals(HttpMethod.POST, draft.rule.method)
    }

    @Test
    fun `unrecognized request falls back to transport-only HTTP criteria`() = runTest {
        val exchange = exchange(pathAndQuery = "/accounts/42", semanticValue = null)
        val useCase = useCase(exchange, SmartProtocolExtension())

        val result = useCase.execute(exchange.id)

        val draft = assertIs<PrepareBreakpointRuleDraftResult.Found>(result).draft
        assertEquals(ProtocolMatchCriteria.HttpDefault, draft.rule.protocolCriteria)
        assertEquals(emptyList(), draft.protocolValues)
        assertEquals("https://api.example.test:443/accounts/42", draft.rule.urlPattern)
    }

    private fun useCase(
        exchange: HttpExchangeSnapshot,
        extension: BreakpointProtocolExtension,
    ): PrepareBreakpointRuleDraftUseCase {
        val trafficQueryPort = FakeTrafficQueryPort(exchange)
        return PrepareBreakpointRuleDraftUseCase(
            loadTrafficExchangeDetailsUseCase = LoadTrafficExchangeDetailsUseCase(trafficQueryPort),
            protocolRegistry = BreakpointProtocolRegistry(listOf(extension)),
        )
    }

    private fun exchange(
        pathAndQuery: String,
        semanticValue: String?,
    ): HttpExchangeSnapshot {
        val bodyId = BodyId("request-body")
        return HttpExchangeSnapshot(
            id = ExchangeId("exchange"),
            request = HttpRequestSnapshot(
                head = RequestHead(
                    method = HttpMethod.POST,
                    target = RequestTarget.Absolute(
                        scheme = HttpScheme.fromToken("https"),
                        authority = Authority("api.example.test", 443),
                        pathAndQuery = pathAndQuery,
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = semanticValue?.let {
                        listOf(HeaderField(HeaderName(SMART_HEADER), it))
                    }.orEmpty(),
                ),
                body = MessageBodyRef.Available(
                    BodyRef(
                        id = bodyId,
                        observedBytes = 2L,
                        storedBytes = 2L,
                        outcome = BodyCaptureOutcome.Complete,
                    ),
                ),
            ),
            state = ExchangeState.COMPLETED,
            startedAtEpochMillis = 1L,
        )
    }

    private class FakeTrafficQueryPort(
        private val exchange: HttpExchangeSnapshot,
    ) : TrafficQueryPort {
        override val generations: Flow<TrafficGeneration> = emptyFlow()

        override suspend fun query(query: TrafficPageQuery): TrafficPage = TrafficPage(
            items = emptyList(),
            nextCursor = null,
            generation = 0L,
        )

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            exchange.takeIf { it.id == exchangeId }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = BodyChunk(
            bytes = "{}".encodeToByteArray(),
            offset = 0L,
            endOfBody = true,
        )
    }
}

private val SMART_PROTOCOL_ID = BreakpointProtocolId("smart-test")
private val SMART_FIELD_ID = ProtocolCriteriaFieldId("semantic-value")
private const val SMART_HEADER = "X-Smart-Test"

private class SmartProtocolExtension : BreakpointProtocolExtension {
    override val definition: BreakpointProtocolDefinition = BreakpointProtocolDefinition(
        protocolId = SMART_PROTOCOL_ID,
        displayName = "Smart Test",
        criteriaVersion = 1,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = SMART_FIELD_ID,
                label = "Semantic value",
                optional = false,
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? =
        SmartCompiledCriteria.takeIf {
            criteria.protocolId == SMART_PROTOCOL_ID && criteria.encodedPayload.isNotBlank()
        }

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = null

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> =
        listOf(ProtocolCriteriaValue(SMART_FIELD_ID, criteria.encodedPayload))

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? = values
        .singleOrNull { it.fieldId == SMART_FIELD_ID }
        ?.value
        ?.takeIf(String::isNotBlank)
        ?.let { ProtocolMatchCriteria(SMART_PROTOCOL_ID, it) }

    override fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? = input.request
        .head
        .headers
        .firstOrNull { it.name.value.equals(SMART_HEADER, ignoreCase = true) }
        ?.value
        ?.let { ProtocolMatchCriteria(SMART_PROTOCOL_ID, it) }
}

private object SmartCompiledCriteria : CompiledProtocolCriteria {
    override val protocolId: BreakpointProtocolId = SMART_PROTOCOL_ID

    override fun matches(observation: ProtocolObservation?): Boolean = false
}
