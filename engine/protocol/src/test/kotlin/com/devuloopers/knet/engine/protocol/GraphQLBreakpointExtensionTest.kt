package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.BreakpointCandidate
import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.contract.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.breakpoint.ProtocolInspectionInput
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointMatch
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointScope
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
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `a GraphQL batch draft requires the user to choose scope`() {
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
        val values = registry.editorValues(criteria)
        assertEquals(listOf(ProtocolCriteriaValue(GraphQLBreakpointMatch.scopeField, GraphQLBreakpointScope.CHOOSE.token)), values)
        assertEquals(null, registry.createCriteria(criteria.protocolId, values))
        val requestCandidate = requestCandidate("batch", "Ignored").copy(
            requestBody = BreakpointBody(body), requestObservedBodyBytes = body.size.toLong())
        val observation = extension.inspect(ProtocolInspectionInput(requestCandidate))
        assertFalse(assertNotNull(registry.compile(criteria)).matches(observation))
    }

    @Test
    fun `persisted drafts match SHA-256 plus wire operation without loading query source`() {
        val registry = BreakpointProtocolRegistry(listOf(extension))
        for (method in listOf(HttpMethod.GET, HttpMethod.POST)) {
            val selected = persistedCandidate("selected", method, "ReadItem")
            val criteria = assertNotNull(registry.suggestCriteria(selected.toSuggestionInput()))
            val values = registry.editorValues(criteria).associate { it.fieldId to it.value }
            assertEquals("exact", values[GraphQLBreakpointMatch.scopeField])
            assertEquals("a".repeat(64), values[GraphQLBreakpointMatch.hashField])
            assertEquals("ReadItem", values[GraphQLBreakpointMatch.requestOperationField])
            assertEquals("", values[GraphQLBreakpointMatch.operationNameField])
            assertEquals(criteria, registry.createCriteria(criteria.protocolId, registry.editorValues(criteria)))
            val matcher = assertNotNull(registry.compile(criteria))
            fun matches(request: BreakpointCandidate) = matcher.matches(extension.inspect(ProtocolInspectionInput(request)))
            assertEquals(true, matches(selected))
            assertFalse(matches(persistedCandidate("other-hash", method, "ReadItem", "b".repeat(64))))
            assertFalse(matches(persistedCandidate("other-operation", method, "ListItems")))
            assertFalse(matches(persistedCandidate("absent-operation", method, null)))
            assertFalse(matches(persistedCandidate("invalid", method, "ReadItem", "invalid")))
            assertFalse(matches(persistedCandidate("wrong-document", method, "ReadItem", query = "query ReadItem { item { id } }")))
            val observation = extension.inspect(ProtocolInspectionInput(selected))
            assertEquals(true, matcher.matches(extension.inspect(ProtocolInspectionInput(responseCandidate(selected), observation))))
        }
    }

    @Test
    fun `saved APQ rule pauses only the matching hash and method on the literal endpoint`() = runTest {
        val selected = persistedCandidate("selected", HttpMethod.POST, "ReadItem")
        val criteria = assertNotNull(extension.suggestCriteria(selected.toSuggestionInput()))
        val coordinator = coordinator(BreakpointPhase.REQUEST, criteria, "https://api.example.test/graphql")
        for (unmatched in listOf(
            persistedCandidate("other-path", HttpMethod.POST, "ReadItem", path = "/graphql/extra"),
            persistedCandidate("other-method", HttpMethod.GET, "ReadItem"),
            persistedCandidate("other-query", HttpMethod.POST, "ListItems"),
        )) {
            assertSame(BreakpointDecision.ContinueUnchanged, coordinator.intercept(unmatched))
        }
        val paused = async { coordinator.intercept(selected) }
        runCurrent()
        val pending = coordinator.pendingBreakpoints.value.single()
        assertEquals(selected.exchangeId, pending.candidate.exchangeId)
        coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged)
        assertSame(BreakpointDecision.ContinueUnchanged, paused.await())
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
        urlPattern: String = "*graphql*",
    ): BreakpointCoordinator = BreakpointCoordinator(
        protocolRegistry = BreakpointProtocolRegistry(listOf(extension)),
    ).also { coordinator ->
        coordinator.replaceRules(
            listOf(
                BreakpointRule(
                    id = "graphql-rule",
                    urlPattern = urlPattern,
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

    private fun persistedCandidate(id: String, method: HttpMethod, operation: String?, hash: String = "a".repeat(64),
        query: String? = null, path: String = "/graphql"): BreakpointCandidate {
        val base = requestCandidate(id, "Unused")
        val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$hash"}}"""
        val envelope = buildString {
            append("""{"extensions":$extensions""")
            operation?.let { append(""","operationName":"$it"""") }
            query?.let { append(""","query":"$it"""") }
            append('}')
        }
        val params = buildList {
            add("extensions=" + URLEncoder.encode(extensions, Charsets.UTF_8))
            operation?.let { add("operationName=" + URLEncoder.encode(it, Charsets.UTF_8)) }
            query?.let { add("query=" + URLEncoder.encode(it, Charsets.UTF_8)) }
        }.joinToString("&", prefix = "?")
        val target = base.request.head.target as RequestTarget.Absolute
        val wireBody = envelope.encodeToByteArray().takeIf { method == HttpMethod.POST }
        return base.copy(
            request = base.request.copy(head = base.request.head.copy(method = method,
                target = target.copy(pathAndQuery = if (method == HttpMethod.GET) path + params else path))),
            requestBody = wireBody?.let(::BreakpointBody),
            requestObservedBodyBytes = wireBody?.size?.toLong() ?: 0,
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
