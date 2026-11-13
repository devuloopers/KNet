package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.domain.payload.*
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.model.*
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import java.net.URLEncoder
import kotlin.test.*

/** API Studio and interception share a raw-preserving editor independent from resolved display documents. */
class GraphQLPersistedQueryEditingTest {
    private val url = "https://persisted-editor.test/graphql"
    private val query = "query Hello { hello }"
    private val id get() = java.security.MessageDigest.getInstance("SHA-256").digest(query.encodeToByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    private val extensions get() = """{"persistedQuery":{"version":1,"sha256Hash":"$id"},"client":{"tag":"keep"}}"""
    private val raw get() = " \n{\"variables\":{\"id\":1},\"extensions\":$extensions,\"client\":{\"unknown\":true}}\n "
    private val sync = SyncBodyStateUseCase(PayloadStrategyRegistry(listOf(GraphQlPayloadMapper())))
    @AfterTest fun clear() = GraphQLHttpRequestFormatter.persistedQueryCatalog.clear()
    private fun learn() {
        val full = "{\"query\":${kotlinx.serialization.json.JsonPrimitive(query)},\"extensions\":$extensions}"
        GraphQLHttpRequestFormatter.inspect("POST", url, body = full.encodeToByteArray(), learnCaptured = true)
    }

    @Test fun `hash-only POST with no operationName opens GraphQL editor with empty query and preserved metadata`() {
        val spec = PayloadInspectionSpec.fromRequest("POST", url, raw.encodeToByteArray())
        assertIs<BodyFormat.GraphQL>(spec.resolvedFormat)
        val state = RequestBodyState.from(spec)
        assertEquals(RequestBodyMode.GRAPHQL, state.mode)
        assertEquals("", state.graphQlState.queryText)
        assertTrue(state.graphQlState.extensionsText.contains(id))
        assertEquals(raw, state.payloadText)
        val tabbed = sync.updateGraphQlState(state, state.graphQlState.copy(activeSubTab = GraphQlSubTab.VARIABLES))
        assertEquals(raw, tabbed.payloadText)
    }

    @Test fun `resolved source appears in viewer but never enters POST or GET authoring fields`() {
        learn()
        val spec = PayloadInspectionSpec.fromRequest("POST", url, raw.encodeToByteArray())
        val format = assertIs<BodyFormat.GraphQL>(spec.resolvedFormat)
        assertTrue(format.queryText.contains("query Hello"))
        assertEquals(GraphQLQueryProvenance.CAPTURED_TRAFFIC, format.queryProvenance)
        val post = RequestBodyState.from(spec)
        assertEquals("", post.graphQlState.queryText)
        assertEquals(raw, post.payloadText)
        val target = "$url?extensions=" + URLEncoder.encode(extensions, Charsets.UTF_8)
        val get = GraphQLParameterEditorState.prepare("GET", target)!!
        assertEquals("", get.graphQl.queryText)
        assertTrue((PayloadInspectionSpec.fromGraphQLParameters("GET", target).resolvedFormat as BodyFormat.GraphQL).queryText.isNotBlank())
    }

    @Test fun `variables-only hash request edit retains hash and unknown fields without injecting resolved query`() {
        learn()
        val state = RequestBodyState.from(PayloadInspectionSpec.fromRequest("POST", url, raw.encodeToByteArray()))
        val changed = sync.updateGraphQlState(state, state.graphQlState.copy(payload = state.graphQlState.payload.copy(variablesText = "{\"id\":2}")))
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(changed.payloadText).toString()
        assertFalse(parsed.contains("\"query\""))
        assertTrue(parsed.contains(id))
        assertTrue(parsed.contains("\"unknown\":true"))
        assertTrue(parsed.contains("\"id\":2"))
    }

    @Test fun `explicit query edit refreshes APQ hash exact source and synchronized structured extension buffer`() {
        val state = RequestBodyState.from(PayloadInspectionSpec.fromRequest("POST", url, raw.encodeToByteArray()))
        val source = "  query Changed { hello }\n"
        val edited = sync.updateGraphQlState(state, state.graphQlState.copy(payload = state.graphQlState.payload.copy(queryText = source)))
        val decoded = GraphQLHttpRequestFormatter.inspect("POST", url, body = edited.payloadText.encodeToByteArray())!!
        assertNull(decoded.diagnostic)
        assertEquals(source, decoded.queryText)
        assertNotEquals(id, decoded.persistedQuery?.hash?.value)
        assertTrue(edited.graphQlState.extensionsText.contains(decoded.persistedQuery!!.hash.value))
        assertTrue(edited.payloadText.contains("\"tag\":\"keep\""))
    }

    @Test fun `variable edit of full-query APQ preserves exact original query despite prettified editor buffer`() {
        val source = " # comment\nquery Hello{hello}\n"
        val sourceId = java.security.MessageDigest.getInstance("SHA-256").digest(source.encodeToByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val body = "{\"query\":${kotlinx.serialization.json.JsonPrimitive(source)},\"extensions\":${extensions.replace(id, sourceId)}}"
        val state = RequestBodyState.from(PayloadInspectionSpec.fromRequest("POST", url, body.encodeToByteArray()))
        val edited = sync.updateGraphQlState(state, state.graphQlState.copy(payload = state.graphQlState.payload.copy(variablesText = "{\"id\":2}")))
        val decoded = GraphQLHttpRequestFormatter.inspect("POST", url, body = edited.payloadText.encodeToByteArray())!!
        assertEquals(source, decoded.queryText)
        assertEquals(sourceId, decoded.persistedQuery?.hash?.value)
        assertNull(decoded.diagnostic)
    }
}
