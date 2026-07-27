package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQLBodyFormatterTest {

    @Test
    fun testGraphQLQueryDetectionAndParsing() {
        val headers = mapOf("content-type" to "application/json")
        val gqlPayload = """
            {
              "operationName": "GetUserDetails",
              "query": "query GetUserDetails(${'$'}id: ID!) { user(id: ${'$'}id) { id name email } }",
              "variables": {
                "id": "usr_1001"
              }
            }
        """.trimIndent()

        val format = BodyFormatterRegistry.resolveFormat(headers, gqlPayload)

        assertTrue(format is BodyFormat.GraphQL)
        assertEquals("Query", format.operationType)
        assertEquals("GetUserDetails", format.operationName)
        assertEquals("GQL: GetUserDetails", format.badgeLabel)
        assertTrue(format.queryText.contains("query GetUserDetails"))
        assertTrue(format.variablesJson.contains("\"usr_1001\""))
    }

    @Test
    fun testGraphQLMutationDetection() {
        val headers = mapOf("content-type" to "application/json")
        val gqlMutation = """
            {
              "operationName": "UpdateCart",
              "query": "mutation UpdateCart(${'$'}itemId: String!) { updateCart(item: ${'$'}itemId) { status } }",
              "variables": {
                "itemId": "item_99"
              }
            }
        """.trimIndent()

        val format = BodyFormatterRegistry.resolveFormat(headers, gqlMutation)

        assertTrue(format is BodyFormat.GraphQL)
        assertEquals("Mutation", format.operationType)
        assertEquals("UpdateCart", format.operationName)
        assertEquals("GQL: UpdateCart", format.badgeLabel)
    }

    @Test
    fun testGraphQLContentTypeHeaderDetection() {
        val headers = mapOf("content-type" to "application/graphql")
        val gqlRaw = "query { healthCheck { status } }"

        val format = BodyFormatterRegistry.resolveFormat(headers, gqlRaw)

        assertTrue(format is BodyFormat.GraphQL)
        assertEquals("GQL: Query", format.badgeLabel)
    }
}
