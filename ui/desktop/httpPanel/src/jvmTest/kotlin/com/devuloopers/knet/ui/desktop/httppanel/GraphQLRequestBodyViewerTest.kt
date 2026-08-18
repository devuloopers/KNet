package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests verifying GraphQL request parsing, formatting, and AST document extraction
 * for the unified Request Inspector.
 */
class GraphQLRequestBodyViewerTest {

    private val formatter = GraphQLBodyFormatter()

    @Test
    fun `GraphQL payload is correctly identified and formatted into AST document and variables`() {
        val rawPayload = """
            {
              "operationName": "FormattedQuotes",
              "variables": {
                "symbols": ["AAPL", "MSFT"],
                "partnerId": 42
              },
              "query": "query FormattedQuotes(${'$'}symbols: [String], ${'$'}partnerId: Int) { formattedQuotes(symbols: ${'$'}symbols, partnerId: ${'$'}partnerId) { __typename symbol last } }"
            }
        """.trimIndent()

        val headers = mapOf("Content-Type" to "application/json")

        assertTrue(formatter.matches(headers, rawPayload), "Formatter should match GraphQL JSON payload")

        val result = formatter.format(headers, rawPayload)
        assertTrue(result is BodyFormat.GraphQL, "Result must be BodyFormat.GraphQL")

        assertEquals("Query", result.operationType)
        assertEquals("FormattedQuotes", result.operationName)
        assertTrue(result.queryText.contains("query FormattedQuotes"), "Query text must contain parsed query name")
        assertTrue(result.queryText.contains("formattedQuotes"), "Query text must contain fields")
        assertTrue(result.variablesJson.contains("\"symbols\""), "Variables JSON must contain formatted variables")
        assertTrue(result.variablesJson.contains("AAPL"), "Variables JSON must contain symbol values")
    }

    @Test
    fun `GraphQL mutation payload is correctly parsed`() {
        val rawPayload = """
            {
              "operationName": "CreateUser",
              "variables": {
                "name": "Alice"
              },
              "query": "mutation CreateUser(${'$'}name: String!) { createUser(name: ${'$'}name) { id name } }"
            }
        """.trimIndent()

        val headers = mapOf("Content-Type" to "application/json")
        val result = formatter.format(headers, rawPayload) as BodyFormat.GraphQL
        assertEquals("Mutation", result.operationType)
        assertEquals("CreateUser", result.operationName)
        assertTrue(result.queryText.contains("mutation CreateUser"))
    }

    @Test
    fun `GraphQL payload with extensions is correctly parsed`() {
        val rawPayload = """
            {
              "operationName": "GetProfile",
              "variables": { "userId": "100" },
              "extensions": {
                "persistedQuery": {
                  "version": 1,
                  "sha256Hash": "abc123hash"
                }
              },
              "query": "query GetProfile(${'$'}userId: ID!) { user(id: ${'$'}userId) { name } }"
            }
        """.trimIndent()

        val headers = mapOf("Content-Type" to "application/json")
        val result = formatter.format(headers, rawPayload) as BodyFormat.GraphQL

        assertEquals("Query", result.operationType)
        assertEquals("GetProfile", result.operationName)
        assertTrue(result.variablesJson.contains("userId"))
        assertTrue(result.extensionsJson.contains("persistedQuery"))
        assertTrue(result.extensionsJson.contains("abc123hash"))
    }

    @Test
    fun `GraphQlSubTab SSOT accessors, updates, and prettifiers operate correctly`() {
        val initialState = com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState(
            payload = com.devuloopers.knet.domain.payload.StructuredPayloadState.GraphQL(
                queryText = "query MyQuery { user { id } }",
                variablesText = "{\"id\":\"123\"}",
                operationName = "MyQuery",
                extensionsText = "{\"client\":\"apollo\"}",
            ),
        )

        // 1. Payloads
        assertEquals("query MyQuery { user { id } }", com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab.QUERY.getPayload(initialState))
        assertEquals("{\"id\":\"123\"}", com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab.VARIABLES.getPayload(initialState))
        assertEquals("{\"client\":\"apollo\"}", com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab.EXTENSIONS.getPayload(initialState))

        // 2. Updates
        val updatedQueryState = com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab.QUERY.updatePayload(initialState, "query NewOp { item }")
        assertEquals("query NewOp { item }", updatedQueryState.queryText)
        assertEquals("NewOp", updatedQueryState.operationName)

        val updatedVarsState = com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab.VARIABLES.updatePayload(initialState, "{\"key\":\"val\"}")
        assertEquals("{\"key\":\"val\"}", updatedVarsState.variablesText)

        // 3. Prettify
        val prettifiedVarsState = com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab.VARIABLES.prettify(updatedVarsState)
        assertEquals("{\n  \"key\": \"val\"\n}", prettifiedVarsState.variablesText)
    }

    @Test
    fun `GraphQLBodySubTab SSOT accessors and empty states resolve accurately`() {
        val format = BodyFormat.GraphQL(
            operationType = "Query",
            operationName = "TestOp",
            queryText = "query TestOp { test }",
            variablesJson = "{}",
            extensionsJson = ""
        )
        val rawJson = "{\"query\":\"query TestOp { test }\"}"

        assertEquals("query TestOp { test }", com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab.QUERY.getPayload(format, rawJson))
        assertEquals(null, com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab.QUERY.getEmptyState(format, rawJson))

        assertEquals("{}", com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab.VARIABLES.getPayload(format, rawJson))
        assertNotNull(com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab.VARIABLES.getEmptyState(format, rawJson))

        assertNotNull(com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab.EXTENSIONS.getEmptyState(format, rawJson))

        assertEquals(rawJson, com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab.RAW_JSON.getPayload(format, rawJson))
        assertEquals(null, com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab.RAW_JSON.getEmptyState(format, rawJson))
    }
}
