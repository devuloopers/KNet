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
    @Test fun `APQ edits refresh only existing valid hash metadata and preserve manual extension edits`() {
        val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"${"a".repeat(64)}","custom":7},"trace":{"keep":true}}"""
        val previous = StructuredPayloadState.GraphQL(extensionsText = extensions)
        val current = previous.copy(queryText = " query New { field }\n")
        val changes = GraphQLHttpRequestFormatter.changedFields(previous, current)
        assertEquals(setOf(GraphQLHttpParameter.QUERY, GraphQLHttpParameter.EXTENSIONS), changes.keys)
        assertTrue(changes[GraphQLHttpParameter.EXTENSIONS]!!.contains("\"custom\":7"))
        val decoded = GraphQLHttpRequestFormatter.inspect("POST", "/graphql", body = GraphQLHttpRequestFormatter.updateJsonBody("{}", changes).getOrThrow().encodeToByteArray())!!
        assertNull(decoded.diagnostic)
        assertEquals(current.queryText, decoded.queryText)
        val manual = current.copy(extensionsText = extensions.replace("true", "false"))
        assertEquals(manual.extensionsText, GraphQLHttpRequestFormatter.changedFields(previous, manual)[GraphQLHttpParameter.EXTENSIONS])
        assertEquals(setOf(GraphQLHttpParameter.VARIABLES), GraphQLHttpRequestFormatter.changedFields(previous, previous.copy(variablesText = "{\"id\":2}")).keys)
    }

    @Test fun `invalid and excessively nested extensions cannot trigger hash rewriting or recursive parsing`() {
        for (extensions in listOf("{broken", "{}", "{\"persistedQuery\":{\"version\":2,\"sha256Hash\":\"${"a".repeat(64)}\"}}",
            "{\"nested\":" + "[".repeat(150) + "0" + "]".repeat(150) + "}")) {
            val previous = StructuredPayloadState.GraphQL(extensionsText = extensions)
            assertEquals(setOf(GraphQLHttpParameter.QUERY), GraphQLHttpRequestFormatter.changedFields(previous, previous.copy(queryText = "{ field }")).keys)
        }
    }

}
