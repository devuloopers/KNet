package com.devuloopers.knet.engine.formatter.graphql

import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.domain.payload.StructuredPayloadState
import kotlin.test.*

/** Edits preserve transport identity and unrelated envelope metadata. */
class GraphQLHttpEditingTest {
    @Test fun `changing only variables leaves original query encoding and selectors alone`() {
        val original = "https://fixture.test/api?query=query%20Read%20%7B%20x%20%7D&extra=a%2bb&extra=2"
        val previous = StructuredPayloadState.GraphQL(queryText = "query Read { x }")
        val current = previous.copy(variablesText = """{"value":"+ & % café"}""")
        val changes = GraphQLHttpRequestFormatter.changedFields(previous, current)
        assertEquals(setOf(GraphQLHttpParameter.VARIABLES), changes.keys)
        val updated = GraphQLHttpRequestFormatter.updateUrl(original, changes).getOrThrow()
        assertTrue(updated.startsWith(original + "&variables="))
        val facts = GraphQLHttpRequestFormatter.inspect("GET", updated)!!
        assertNull(facts.suppliedOperationName)
        assertEquals("Read", facts.selectedOperation?.name)
        assertEquals(current.variablesText, facts.variablesJson)
    }

    @Test fun `POST changes retain unknown envelope data and original selector`() {
        val source = """{"query":"query A { a } query B { b }","operationName":"B","client":{"id":7},"variables":{"old":1}}"""
        val changed = GraphQLHttpRequestFormatter.updateJsonBody(source,
            mapOf(GraphQLHttpParameter.VARIABLES to """{"new":2}""")).getOrThrow()
        assertTrue(changed.contains("\"client\":{\"id\":7}"))
        val facts = GraphQLHttpRequestFormatter.inspect("POST", "/api", body = changed.encodeToByteArray())!!
        assertEquals("B", facts.selectedOperation?.name)
        assertEquals("B", facts.suppliedOperationName)
        assertEquals("{\"new\":2}", facts.variablesJson)
    }

    @Test fun `invalid edited JSON stays diagnosable instead of silently removing variables`() {
        val source = "{\"query\":\"query Read { x }\",\"variables\":{\"old\":1}}"
        val changed = GraphQLHttpRequestFormatter.updateJsonBody(source, mapOf(GraphQLHttpParameter.VARIABLES to "{oops")).getOrThrow()
        assertTrue(changed.contains("{oops"))
        assertEquals(GraphQLHttpDiagnostic.MALFORMED_ENVELOPE,
            GraphQLHttpRequestFormatter.inspect("POST", "/api", body = changed.encodeToByteArray())!!.diagnostic)
    }

    @Test fun `multi operation authoring never chooses the first operation implicitly`() {
        val source = "query A { a } query B { b }"
        assertNull(GraphQLQuerySynchronizer.extractOperationName(source))
        assertEquals(source, GraphQLQuerySynchronizer.updateOperationName(source, "B"))
    }
}
