package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.domain.protocol.inspector.registry.ProtocolInspectorRegistry
import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLProtocolInspector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GraphQLProtocolInspectorTest {

    private val inspector = GraphQLProtocolInspector()
    private val registry = ProtocolInspectorRegistry(listOf(inspector))

    @Test
    fun `inspect returns GraphQL metadata when query payload is intercepted`() {
        val payload = """
            {
                "operationName": "GetUserProfile",
                "query": "query GetUserProfile(${'$'}id: ID!) { user(id: ${'$'}id) { name email } }",
                "variables": { "id": "42" }
            }
        """.trimIndent().encodeToByteArray()

        val result = inspector.inspect("POST", "https://api.example.com/graphql", emptyMap(), payload)

        assertIs<InterceptionMetadata.GraphQL>(result)
        assertEquals("GetUserProfile", result.operationName)
        assertEquals("Query", result.operationType)
    }

    @Test
    fun `inspect extracts mutation operation type correctly`() {
        val payload = """
            {
                "operationName": "UpdateCart",
                "query": "mutation UpdateCart(${'$'}itemId: ID!) { addToCart(id: ${'$'}itemId) { count } }",
                "variables": {}
            }
        """.trimIndent().encodeToByteArray()

        val result = inspector.inspect("POST", "https://api.example.com/graphql", emptyMap(), payload)

        assertIs<InterceptionMetadata.GraphQL>(result)
        assertEquals("UpdateCart", result.operationName)
        assertEquals("Mutation", result.operationType)
    }

    @Test
    fun `inspect extracts inline operation name from query string when operationName field is missing`() {
        val payload = """
            {
                "query": "query FetchInventory { inventory { items { id } } }"
            }
        """.trimIndent().encodeToByteArray()

        val result = inspector.inspect("POST", "https://api.example.com/graphql", emptyMap(), payload)

        assertIs<InterceptionMetadata.GraphQL>(result)
        assertEquals("FetchInventory", result.operationName)
        assertEquals("Query", result.operationType)
    }

    @Test
    fun `inspect fast path returns GraphQL metadata for URL match when body is empty`() {
        val result = inspector.inspect("POST", "https://stg-04astra.cnbc.com/graphql", emptyMap(), ByteArray(0))

        assertIs<InterceptionMetadata.GraphQL>(result)
        assertEquals("Query", result.operationType)
    }

    @Test
    fun `inspect returns null for non-GraphQL POST payload`() {
        val payload = """
            {
                "name": "John Doe",
                "email": "john@example.com"
            }
        """.trimIndent().encodeToByteArray()

        val result = inspector.inspect("POST", "https://api.example.com/users", emptyMap(), payload)

        assertNull(result)
    }

    @Test
    fun `ProtocolInspectorRegistry falls back to GenericHttp for standard requests`() {
        val result = registry.inspect("GET", "https://api.example.com/users", emptyMap(), ByteArray(0))

        assertIs<InterceptionMetadata.GenericHttp>(result)
    }
}
