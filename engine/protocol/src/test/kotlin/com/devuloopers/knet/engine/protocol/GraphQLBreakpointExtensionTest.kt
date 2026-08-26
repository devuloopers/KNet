package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.BreakpointCandidate
import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.contract.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointExtension
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointProtocol
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLDocumentParser
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLOperationType
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class GraphQLBreakpointExtensionTest {
    private val parser = GraphQLDocumentParser()
    private val extension = GraphQLBreakpointExtension(parser)

    @Test
    fun `parser extracts typed operations from a GraphQL batch`() {
        val document = parser.parse(
            """[
                {"operationName":"GetProfile","query":"query GetProfile { viewer { id } }"},
                {"query":"mutation UpdateProfile { updateProfile { id } }"}
            ]""".encodeToByteArray(),
        )

        assertNotNull(document)
        assertEquals(listOf("GetProfile", "UpdateProfile"), document.operations.map { it.name })
        assertEquals(
            listOf(GraphQLOperationType.QUERY, GraphQLOperationType.MUTATION),
            document.operations.map { it.type },
        )
    }

    @Test
    fun `request rule pauses only its selected GraphQL operation`() = runTest {
        val coordinator = coordinator(BreakpointPhase.REQUEST, criteria("GetProfile"))

        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(requestCandidate("other", "UpdateProfile")),
        )

        val decision = async { coordinator.intercept(requestCandidate("matching", "GetProfile")) }
        runCurrent()
        val pending = coordinator.pendingBreakpoints.value.single()
        assertEquals("graphql-rule", pending.ruleId)
        coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged)
        assertSame(BreakpointDecision.ContinueUnchanged, decision.await())
    }

    @Test
    fun `blank operation criteria intentionally matches any detected GraphQL request`() = runTest {
        val coordinator = coordinator(BreakpointPhase.REQUEST, criteria(null))

        val decision = async { coordinator.intercept(requestCandidate("any-operation", "UpdateProfile")) }
        runCurrent()
        val pending = coordinator.pendingBreakpoints.value.single()
        coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged)

        assertSame(BreakpointDecision.ContinueUnchanged, decision.await())
    }

    @Test
    fun `smart drafts distinguish operation names on the same GraphQL endpoint`() {
        val registry = BreakpointProtocolRegistry(listOf(extension))
        val getProfile = requestCandidate("get-profile", "GetProfile")
        val updateProfile = requestCandidate("update-profile", "UpdateProfile")

        assertEquals(getProfile.request.head.target, updateProfile.request.head.target)
        val getCriteria = assertNotNull(
            registry.suggestCriteria(getProfile.toSuggestionInput()),
        )
        val updateCriteria = assertNotNull(
            registry.suggestCriteria(updateProfile.toSuggestionInput()),
        )

        assertEquals(GraphQLBreakpointProtocol.id, getCriteria.protocolId)
        assertEquals(
            "GetProfile",
            registry.editorValues(getCriteria).single().value,
        )
        assertEquals(
            "UpdateProfile",
            registry.editorValues(updateCriteria).single().value,
        )
    }

    @Test
    fun `smart draft keeps a GraphQL batch endpoint scoped`() {
        val registry = BreakpointProtocolRegistry(listOf(extension))
        val request = requestCandidate("batch", "Ignored").request
        val body = """[
            {"operationName":"GetProfile","query":"query GetProfile { viewer { id } }"},
            {"operationName":"UpdateProfile","query":"mutation UpdateProfile { updateProfile { id } }"}
        ]""".encodeToByteArray()

        val criteria = assertNotNull(
            registry.suggestCriteria(
                BreakpointRuleSuggestionInput(
                    request = request,
                    requestBody = BreakpointBody(body),
                    requestBodyComplete = true,
                ),
            ),
        )

        assertEquals(GraphQLBreakpointProtocol.id, criteria.protocolId)
        assertEquals("", registry.editorValues(criteria).single().value)
    }

    @Test
    fun `response rule reuses compact request observation by exchange identity`() = runTest {
        val coordinator = coordinator(BreakpointPhase.RESPONSE, criteria("GetProfile"))
        val request = requestCandidate("response-match", "GetProfile")

        assertSame(BreakpointDecision.ContinueUnchanged, coordinator.intercept(request))
        val decision = async { coordinator.intercept(responseCandidate(request)) }
        runCurrent()

        val pending = coordinator.pendingBreakpoints.value.single()
        assertEquals(BreakpointPhase.RESPONSE, pending.candidate.phase)
        coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged)
        assertSame(BreakpointDecision.ContinueUnchanged, decision.await())
        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(responseCandidate(request)),
            "A completed response must not leave reusable protocol observations behind.",
        )
    }

    @Test
    fun `response rule does not match a different retained operation`() = runTest {
        val coordinator = coordinator(BreakpointPhase.RESPONSE, criteria("GetProfile"))
        val request = requestCandidate("response-other", "UpdateProfile")

        assertSame(BreakpointDecision.ContinueUnchanged, coordinator.intercept(request))
        assertSame(BreakpointDecision.ContinueUnchanged, coordinator.intercept(responseCandidate(request)))
        assertEquals(emptyList(), coordinator.pendingBreakpoints.value)
    }

    private fun coordinator(
        phase: BreakpointPhase,
        criteria: ProtocolMatchCriteria,
    ): BreakpointCoordinator = BreakpointCoordinator(
        protocolRegistry = BreakpointProtocolRegistry(listOf(extension)),
    ).also { coordinator ->
        coordinator.replaceRules(
            listOf(
                BreakpointRule(
                    id = "graphql-rule",
                    urlPattern = "*graphql*",
                    method = HttpMethod.POST,
                    phase = phase,
                    protocolCriteria = criteria,
                ),
            ),
        )
    }

    private fun criteria(operationName: String?): ProtocolMatchCriteria = requireNotNull(
        extension.createCriteria(
            listOf(
                ProtocolCriteriaValue(
                    GraphQLBreakpointProtocol.operationNameFieldId,
                    operationName.orEmpty(),
                ),
            ),
        ),
    )

    private fun requestCandidate(id: String, operationName: String): BreakpointCandidate {
        val request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.POST,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("api.example.test"),
                    pathAndQuery = "/graphql",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = listOf(HeaderField(HeaderName("Content-Type"), "application/json")),
            ),
        )
        val body = """{"operationName":"$operationName","query":"query $operationName { viewer { id } }"}"""
            .encodeToByteArray()
        return BreakpointCandidate(
            exchangeId = ExchangeId(id),
            phase = BreakpointPhase.REQUEST,
            request = request,
            requestBody = BreakpointBody(body),
            requestObservedBodyBytes = body.size.toLong(),
            startedAtEpochMillis = 1L,
        )
    }

    private fun responseCandidate(request: BreakpointCandidate): BreakpointCandidate = BreakpointCandidate(
        exchangeId = request.exchangeId,
        phase = BreakpointPhase.RESPONSE,
        request = request.request,
        response = HttpResponseSnapshot(
            ResponseHead(
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                status = HttpStatus(200),
                reasonPhrase = "OK",
                headers = emptyList(),
            ),
        ),
        startedAtEpochMillis = request.startedAtEpochMillis,
    )

    private fun BreakpointCandidate.toSuggestionInput(): BreakpointRuleSuggestionInput =
        BreakpointRuleSuggestionInput(
            request = request,
            requestBody = requestBody,
            requestBodyComplete = requestObservedBodyBytes == requestBody?.size?.toLong(),
        )
}
