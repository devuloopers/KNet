package com.devuloopers.knet.data.desktop.traffic

import com.devuloopers.knet.domain.graphql.GraphQLHttpSource
import kotlin.test.*

/** The real adapter shares inspector formatting and preserves malformed or unavailable evidence. */
class DesktopTrafficDetailsFormattingTest {
    private val formatter = DesktopTrafficDetailsFormatting()
    @Test fun `strict JSON uses readable indentation while malformed deep and arbitrary text remain intact`() {
        assertEquals("{\n  \"ok\": true\n}", formatter.json("{\"ok\":true}"))
        for (raw in listOf("not JSON", "{\"broken\":", "{".repeat(256) + "}".repeat(256))) assertEquals(raw, formatter.json(raw))
    }
    @Test fun `URL and POST GraphQL keep query selector variables and extensions separately`() {
        val get = assertNotNull(formatter.graphQL("GET", "https://copy.test/graphql?query=query+Q%7Bid%7D&operationName=Q&variables=%7B%22id%22%3A1%7D", "", null, true))
        assertEquals(GraphQLHttpSource.URL_PARAMETERS, get.source); assertEquals("Q", get.selectedOperation?.name); assertContains(get.variablesJson, "id")
        val post = assertNotNull(formatter.graphQL("POST", "https://copy.test/graphql", "application/json", "{\"query\":\"query P { id }\",\"variables\":{},\"extensions\":{}}".encodeToByteArray(), true))
        assertEquals(GraphQLHttpSource.JSON_BODY, post.source); assertEquals("P", post.selectedOperation?.name)
        assertNull(formatter.graphQL("GET", "https://copy.test/users?page=1", "", null, true))
    }
}
