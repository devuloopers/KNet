package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.domain.payload.PayloadStrategyRegistry
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter
import com.devuloopers.knet.domain.graphql.GraphQLHttpParameter
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.model.*
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import kotlin.test.*

/** The same authoring adapters serve API Studio and interception without moving URL data into a body. */
class GraphQLSourceEditingTest {
    @Test fun `validation-only GET preparation keeps raw input and never prettifies incomplete drafts`() {
        val source = "/startup?query=query+Read+%7Bx%7D&variables=%7B%22id%22%3A7%7D"
        val raw = GraphQLParameterEditorState.prepare("GET", source, prettify = false)!!.graphQl
        assertEquals("query Read {x}", raw.queryText)
        assertEquals("{\"id\":7}", raw.variablesText)
        val malformed = GraphQLParameterEditorState.prepare("GET", source + "&extensions=%7Bbroken")!!
        assertEquals("{broken", malformed.graphQl.extensionsText)
        assertNotNull(malformed.diagnostic)
    }

    @Test fun `shared GET editor hydration prettifies the first display without editing the canonical URL`() {
        val source = "/startup?query=query+Read+%7B+viewer+%7B+id+%7D+%7D&operationName=Read" +
            "&variables=%7B%22id%22%3A7%7D&extensions=%7B%22client%22%3A%7B%22id%22%3A1%7D%7D"
        val prepared = GraphQLParameterEditorState.prepare("GET", source)!!
        assertTrue(prepared.graphQl.queryText.contains("\n"))
        assertEquals("{\n  \"id\": 7\n}", prepared.graphQl.variablesText)
        assertTrue(prepared.graphQl.extensionsText.contains("\n    \"id\": 1\n"))
        assertEquals("Read", prepared.graphQl.operationName)
        assertEquals(source, GraphQLHttpRequestFormatter.updateUrl(source,
            GraphQLHttpRequestFormatter.changedFields(prepared.graphQl.payload, prepared.graphQl.payload)).getOrThrow())
    }

    @Test fun `anonymous query editing clears a stale selector while multiple operations retain explicit selection`() {
        val state = GraphQlState(com.devuloopers.knet.domain.payload.StructuredPayloadState.GraphQL(queryText = "query Read { x }", operationName = "Read"))
        assertEquals("", GraphQlSubTab.QUERY.updatePayload(state, "{ x }").operationName)
        assertEquals("Read", GraphQlSubTab.QUERY.updatePayload(state, "query Read { x } query Other { y }").operationName)
    }

    @Test fun `POST hydration and tab changes retain exact body while field edits retain unrelated data`() {
        val original = " \n{\"query\":\"query A { a } query B { b }\",\"operationName\":\"B\",\"client\":{\"id\":7},\"variables\":{\"old\":1}}\n "
        val state = RequestBodyState.from(PayloadInspectionSpec.fromPayload(listOf("Content-Type" to "application/json"), original))
        assertEquals(original, state.payloadText)
        val sync = SyncBodyStateUseCase(PayloadStrategyRegistry(listOf(GraphQlPayloadMapper())))
        val selected = sync.updateGraphQlState(state, state.graphQlState.copy(activeSubTab = GraphQlSubTab.VARIABLES))
        assertEquals(original, selected.payloadText)
        val edited = sync.updateGraphQlState(selected, selected.graphQlState.copy(payload = selected.graphQlState.payload.copy(variablesText = "{\"new\":2}")))
        assertTrue(edited.payloadText.contains("\"client\":{\"id\":7}"))
        val facts = GraphQLHttpRequestFormatter.inspect("POST", "/api", body = edited.payloadText.encodeToByteArray())!!
        assertEquals("B", facts.selectedOperation?.name)
        assertEquals("{\"new\":2}", facts.variablesJson)
    }

    @Test fun `GET parameter hydration is independent of body state and preserves supplied selector`() {
        val source = "/startup?query=query+A+%7Ba%7D+query+B+%7Bb%7D&operationName=B&variables=%7B%22id%22%3A7%7D"
        val state = GraphQLParameterEditorState.prepare("GET", source)!!
        assertEquals("B", state.graphQl.operationName)
        assertTrue(state.graphQl.queryText.contains("query A"))
        assertTrue(state.graphQl.variablesText.contains("7"))
        assertEquals(RequestBodyMode.NONE, RequestBodyState.from(PayloadInspectionSpec.EMPTY).mode)
        assertNull(GraphQLParameterEditorState.prepare("POST", source))
        assertNull(GraphQLParameterEditorState.prepare("GET", "/ordinary?search=1"))
    }

    @Test fun `structured GET interception edits keep target form authority method and unrelated pairs`() {
        val path = "/api?tracking=a%2bb&query=query+Read+%7Bx%7D&tracking=2"
        for (target in listOf(RequestTarget.Origin(path), RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("fixture.test", 443), path))) {
            val edited = GraphQLHttpRequestFormatter.updateTarget(target, mapOf(GraphQLHttpParameter.QUERY to "query Updated { x }")).getOrThrow()
            val actual = when (edited) {
                is RequestTarget.Absolute -> { assertEquals((target as RequestTarget.Absolute).authority, edited.authority); edited.pathAndQuery }
                is RequestTarget.Origin -> edited.pathAndQuery
                else -> error("Unexpected target form")
            }
            assertTrue(actual.startsWith("/api?tracking=a%2bb&query="))
            assertTrue(actual.endsWith("&tracking=2"))
            assertEquals("Updated", GraphQLHttpRequestFormatter.inspect("GET", actual)!!.selectedOperation?.name)
        }
    }
}
