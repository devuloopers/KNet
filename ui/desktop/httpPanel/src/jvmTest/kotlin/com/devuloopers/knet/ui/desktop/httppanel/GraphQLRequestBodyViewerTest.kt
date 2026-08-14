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
}
