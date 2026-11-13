package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.breakpoint.*
import com.devuloopers.knet.application.contract.networkconditions.*
import com.devuloopers.knet.application.contract.inspection.*
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.engine.protocol.inspector.graphql.*
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Verified persisted documents participate in live rules; unknown hashes remain conservative. */
class GraphQLPersistedQueryConsumersTest {
    private val catalog = BoundedGraphQLPersistedQueryCatalog()
    private val parser = GraphQLDocumentParser(GraphQLPersistedQueryResolver(catalog))
    private val query = "query GetItem { item { id } }"
    private val hash = GraphQLQueryHashing.sha256(query)
    private fun body(full: Boolean) = buildJsonObject {
        if (full) put("query", query)
        put("operationName", "GetItem")
        put("extensions", buildJsonObject { put("persistedQuery", buildJsonObject {
            put("version", 1); put("sha256Hash", hash.value)
        }) })
    }.toString().encodeToByteArray()
    private fun request() = HttpRequestSnapshot(RequestHead(HttpMethod.POST,
        RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("fixture.test", 443), "/graphql"),
        ApplicationProtocol.fromToken("HTTP/2"), listOf(HeaderField(HeaderName("Content-Type"), "application/json"))))

    @Test fun `breakpoints learn a complete fallback and match later hash-only request and response`() {
        val extension = GraphQLBreakpointExtension(parser)
        val compiled = extension.compile(extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLBreakpointProtocol.operationNameFieldId, "GetItem")))!!)!!
        fun candidate(full: Boolean) = BreakpointCandidate(ExchangeId("fixture"), BreakpointPhase.REQUEST, request(),
            requestBody = BreakpointBody(body(full)), requestObservedBodyBytes = body(full).size.toLong(), startedAtEpochMillis = 1)
        assertFalse(compiled.matches(extension.inspect(ProtocolInspectionInput(candidate(false)))))
        assertTrue(compiled.matches(extension.inspect(ProtocolInspectionInput(candidate(true)))))
        val observation = extension.inspect(ProtocolInspectionInput(candidate(false)))
        assertTrue(compiled.matches(observation))
        assertTrue(compiled.matches(extension.inspect(ProtocolInspectionInput(candidate(false).copy(
            phase = BreakpointPhase.RESPONSE, requestBody = null, response = HttpResponseSnapshot(ResponseHead(
                ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), headers = emptyList()))), observation))))
    }

    @Test fun `network conditions require known operation facts and do not learn truncated bodies`() {
        val extension = GraphQLNetworkConditionExtension(parser)
        val compiled = extension.compile(extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, "GetItem"),
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, "query")))!!)!!
        fun input(full: Boolean, complete: Boolean = true) = NetworkConditionHttpInspectionInput(request(),
            NetworkConditionBody(body(full)), requestBodyComplete = complete)
        assertFalse(compiled.matches(extension.inspectHttp(input(false))))
        assertFalse(compiled.matches(extension.inspectHttp(input(true, false))))
        assertFalse(compiled.matches(extension.inspectHttp(input(false))))
        assertTrue(compiled.matches(extension.inspectHttp(input(true))))
        assertTrue(compiled.matches(extension.inspectHttp(input(false))))
        assertEquals(listOf("GetItem", "query"), extension.editorValues(extension.suggestCriteria(input(false))!!).map { it.value })
    }

    @Test fun `semantic metadata reports source hash and verified operation without inventing HTTP query bytes`() = runTest {
        val inspector = GraphQLSemanticInspector(parser)
        val exchange = HttpExchangeSnapshot(ExchangeId("fixture"), request = request(), state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
        fun input(full: Boolean) = SemanticInspectionInput(exchange, InspectionBody(listOf(BodyChunk(body(full), 0, true)), false), null)
        val unknown = inspector.inspect(input(false))!!
        assertTrue(unknown.fields.none { it.label == "Operation name" })
        assertEquals("Query source unavailable", unknown.fields.single { it.label == "Query document source" }.value)
        inspector.inspect(input(true))
        val resolved = inspector.inspect(input(false))!!
        assertEquals("GetItem", resolved.fields.single { it.label == "Operation name" }.value)
        assertEquals(hash.value, resolved.fields.single { it.label == "Persisted query SHA-256" }.value)
        assertEquals("Captured traffic", resolved.fields.single { it.label == "Query document source" }.value)
    }
    private fun gzip(bytes: ByteArray): ByteArray = java.io.ByteArrayOutputStream().use { output ->
        java.util.zip.GZIPOutputStream(output).use { it.write(bytes) }; output.toByteArray()
    }

    @Test fun `complete compressed fallback learns through live matching and resolves compressed hash only input`() {
        val extension = GraphQLNetworkConditionExtension(parser)
        val compiled = extension.compile(extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, "GetItem"),
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, "query")))!!)!!
        val encodedRequest = request().let { it.copy(head = it.head.copy(headers = it.head.headers + HeaderField(HeaderName("Content-Encoding"), "gzip"))) }
        fun input(full: Boolean, complete: Boolean = true) = NetworkConditionHttpInspectionInput(encodedRequest,
            NetworkConditionBody(gzip(body(full))), requestBodyComplete = complete)
        assertFalse(compiled.matches(extension.inspectHttp(input(true, false))))
        assertFalse(compiled.matches(extension.inspectHttp(input(false))))
        assertTrue(compiled.matches(extension.inspectHttp(input(true))))
        assertTrue(compiled.matches(extension.inspectHttp(input(false))))
    }

    @Test fun `corrupt unsupported and inflated over budget bodies never teach a query`() {
        val headers = listOf("Content-Encoding" to "gzip")
        val limit = parser.decodeHttp("POST", "https://fixture.test/graphql", body = gzip(ByteArray(1_048_577) { 32 }),
            learnCaptured = true, headers = headers)!!
        assertEquals(com.devuloopers.knet.domain.graphql.GraphQLHttpDiagnostic.LIMIT_EXCEEDED, limit.diagnostic)
        for (encoding in listOf("gzip", "unknown-content-encoding")) {
            val bad = parser.decodeHttp("POST", "https://fixture.test/graphql", body = body(true), learnCaptured = true,
                headers = listOf("Content-Encoding" to encoding))!!
            assertTrue(bad.matchableOperations.isEmpty())
        }
        assertNull(catalog.lookup(catalog.endpoint("https://fixture.test/graphql")!!, hash))
    }

    @Test fun `asynchronous semantic inspection learns compressed captured source without Traffic selection`() = runTest {
        val encodedRequest = request().let { it.copy(head = it.head.copy(headers = it.head.headers + HeaderField(HeaderName("Content-Encoding"), "gzip"))) }
        val exchange = HttpExchangeSnapshot(ExchangeId("compressed"), request = encodedRequest, state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
        val inspector = GraphQLSemanticInspector(parser)
        val document = inspector.inspect(SemanticInspectionInput(exchange,
            InspectionBody(listOf(BodyChunk(gzip(body(true)), 0, true)), false), null))!!
        assertEquals("GetItem", document.fields.single { it.label == "Operation name" }.value)
        assertEquals(query, catalog.lookup(catalog.endpoint("https://fixture.test/graphql")!!, hash)?.query)
    }

    @Test fun `capture time unavailable annotation does not suppress current verified operation quick add`() {
        val extension = GraphQLNetworkConditionExtension(parser)
        val input = NetworkConditionHttpInspectionInput(request(), NetworkConditionBody(body(false)), requestBodyComplete = true,
            trafficAnnotations = listOf(com.devuloopers.knet.traffic.inspection.InspectionAnnotation(ExchangeId("fixture"),
                com.devuloopers.knet.traffic.inspection.InspectorId("graphql"), 3,
                com.devuloopers.knet.traffic.inspection.InspectionAnnotationState.COMPLETED,
                com.devuloopers.knet.traffic.inspection.InspectionDocument("graphql", "Persisted request", fields = listOf(
                    com.devuloopers.knet.traffic.inspection.InspectionField("Query document source", "Query source unavailable"))),
                createdAtEpochMillis = 1)))
        extension.inspectHttp(input.copy(requestBody = NetworkConditionBody(body(true))))
        assertEquals(listOf("GetItem", "query"), extension.editorValues(extension.suggestCriteria(input)!!).map { it.value })
    }

}
