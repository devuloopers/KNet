package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQLBodyFormatterTest {
    private val formatter = GraphQLBodyFormatter()

    @Test
    fun testGraphQLParsing() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_GRAPHQL))

        val result = formatter.format(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_GRAPHQL)
        assertTrue(result is BodyFormat.GraphQL)
        assertEquals("Query", result.operationType)
        assertEquals("GetUser", result.operationName)
        assertTrue(result.variablesJson.contains("123"))
    }
}
