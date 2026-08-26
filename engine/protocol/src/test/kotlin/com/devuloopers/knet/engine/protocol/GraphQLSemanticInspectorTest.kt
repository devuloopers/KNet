package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.inspection.InspectionBody
import com.devuloopers.knet.application.contract.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLSemanticInspector
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphQLSemanticInspectorTest {
    private val inspector = GraphQLSemanticInspector()

    @Test
    fun `produces a generic GraphQL document from a bounded body`() = runBlocking {
        val payload = """
            {
                "operationName": "GetUserProfile",
                "query": "query GetUserProfile(${'$'}id: ID!) { user(id: ${'$'}id) { name email } }"
            }
        """.trimIndent().encodeToByteArray()
        val exchange = exchange("POST", "/graphql", "application/json")

        val result = inspector.inspect(
            SemanticInspectionInput(
                exchange = exchange,
                requestBody = InspectionBody(
                    chunks = listOf(BodyChunk(payload, 0L, true)),
                    truncated = false,
                ),
                responseBody = null,
            ),
        )

        assertEquals("graphql", result?.kind)
        assertEquals("GraphQL Query: GetUserProfile", result?.title)
        assertEquals("GetUserProfile", result?.fields?.first { it.label == "Operation name" }?.value)
    }

    @Test
    fun `metadata predicate is cheap and body parsing rejects ordinary JSON`() = runBlocking {
        val exchange = exchange("POST", "/users", "application/json")
        assertTrue(inspector.supports(exchange))
        val result = inspector.inspect(
            SemanticInspectionInput(
                exchange,
                InspectionBody(listOf(BodyChunk("{\"name\":\"Ada\"}".encodeToByteArray(), 0L, true)), false),
                null,
            ),
        )
        assertNull(result)
        assertFalse(inspector.supports(exchange("GET", "/users", null)))
    }

    private fun exchange(method: String, path: String, contentType: String?): HttpExchangeSnapshot =
        HttpExchangeSnapshot(
            id = ExchangeId("exchange-${method.lowercase()}-${path.hashCode()}"),
            request = HttpRequestSnapshot(
                RequestHead(
                    method = HttpMethod.fromToken(method),
                    target = RequestTarget.Absolute(
                        HttpScheme.fromToken("https"),
                        Authority("api.example.com"),
                        path,
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = contentType?.let {
                        listOf(HeaderField(HeaderName("Content-Type"), it))
                    }.orEmpty(),
                ),
            ),
            state = ExchangeState.COMPLETED,
            startedAtEpochMillis = 1L,
        )
}
