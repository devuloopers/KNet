package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.breakpoint.*
import com.devuloopers.knet.application.contract.networkconditions.*
import com.devuloopers.knet.application.contract.inspection.*
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.engine.protocol.inspector.graphql.*
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import kotlinx.coroutines.test.runTest
import java.net.URLEncoder
import kotlin.test.*

/** The same decoded operation facts drive detection, rule suggestions and both matching phases. */
class GraphQLHttpConsumerTest {
    private val query = "# startup\nquery BootStrapQuery { bootstrap { id } }"
    private val json = """{"query":"query BootStrapQuery { bootstrap { id } }"}"""
    private fun request(method: HttpMethod, target: String) = HttpRequestSnapshot(RequestHead(method,
        RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("fixture.test", 443), target),
        ApplicationProtocol.fromToken("HTTP/2"), listOf(HeaderField(HeaderName("Content-Type"), "application/json"))))
    private fun get(target: String = "/startup?query=" + URLEncoder.encode(query, Charsets.UTF_8)) = request(HttpMethod.GET, target)
    private fun candidate(get: Boolean) = BreakpointCandidate(ExchangeId("fixture"), BreakpointPhase.REQUEST,
        if (get) get() else request(HttpMethod.POST, "/startup"),
        requestBody = if (get) null else BreakpointBody(json.encodeToByteArray()), startedAtEpochMillis = 1)

    @Test fun `GET and POST breakpoint suggestions and matching are equivalent`() {
        val extension = GraphQLBreakpointExtension()
        val criteria = extension.createCriteria(listOf(ProtocolCriteriaValue(GraphQLBreakpointProtocol.operationNameFieldId, "BootStrapQuery")))!!
        val compiled = extension.compile(criteria)!!
        for (get in listOf(true, false)) {
            val candidate = candidate(get)
            val observation = extension.inspect(ProtocolInspectionInput(candidate))
            assertTrue(compiled.matches(observation))
            val suggested = extension.suggestCriteria(BreakpointRuleSuggestionInput(candidate.request, candidate.requestBody, !get))!!
            assertEquals("BootStrapQuery", extension.editorValues(suggested).single().value)
            val response = candidate.copy(phase = BreakpointPhase.RESPONSE, requestBody = null,
                response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), headers = emptyList())))
            assertTrue(compiled.matches(extension.inspect(ProtocolInspectionInput(response, observation))))
        }
    }

    @Test fun `URL name alone ambiguous selection and GET mutations never trigger named breakpoints`() {
        val extension = GraphQLBreakpointExtension()
        val compiled = extension.compile(extension.createCriteria(listOf(ProtocolCriteriaValue(GraphQLBreakpointProtocol.operationNameFieldId, "BootStrapQuery")))!!)!!
        for (target in listOf("/graphql?operationName=BootStrapQuery", "/graphql?query=query+BootStrapQuery+%7Bx%7D&query=query+Other+%7By%7D",
            "/graphql?query=mutation+BootStrapQuery+%7Bx%7D", "/graphql?query=query+BootStrapQuery+%7Bx%7D+query+Other+%7By%7D")) {
            assertFalse(compiled.matches(extension.inspect(ProtocolInspectionInput(candidate(true).copy(request = get(target))))), target)
        }
    }

    @Test fun `GET network conditions identify operation without body aggregation`() {
        val extension = GraphQLNetworkConditionExtension()
        val criteria = extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, "BootStrapQuery"),
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, "query")))!!
        val compiled = extension.compile(criteria)!!
        val input = NetworkConditionHttpInspectionInput(get(), requestBody = null, requestBodyComplete = false)
        assertFalse(extension.shouldInspectHttpBody(input))
        assertTrue(compiled.matches(extension.inspectHttp(input)))
        assertEquals(listOf("BootStrapQuery", "query"), extension.editorValues(extension.suggestCriteria(input)!!).map { it.value })
        val post = input.copy(request = request(HttpMethod.POST, "/startup"), requestBody = NetworkConditionBody(json.encodeToByteArray()), requestBodyComplete = true)
        assertTrue(extension.shouldInspectHttpBody(post))
        assertTrue(compiled.matches(extension.inspectHttp(post)))
        assertFalse(compiled.matches(extension.inspectHttp(post.copy(requestBodyComplete = false))))
    }

    @Test fun `semantic GET inspection reports source actual operation and invalid method honestly`() = runTest {
        val inspector = GraphQLSemanticInspector()
        val exchange = HttpExchangeSnapshot(ExchangeId("fixture"), request = get(), state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
        assertTrue(inspector.supports(exchange))
        val document = inspector.inspect(SemanticInspectionInput(exchange, null, null))!!
        assertEquals("GraphQL Query: BootStrapQuery", document.title)
        assertEquals("URL parameters", document.fields.single { it.label == "Source" }.value)
        assertEquals(2L, inspector.schemaVersion)
        val invalid = exchange.copy(request = get("/graphql?query=mutation+Write+%7Bx%7D"))
        val invalidDocument = inspector.inspect(SemanticInspectionInput(invalid, null, null))!!
        assertEquals("Mutation", invalidDocument.fields.single { it.label == "Operation type" }.value)
        assertTrue(invalidDocument.fields.any { it.label == "Inspection" && it.value.contains("GET") })
    }
}
