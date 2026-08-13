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

    @Test
    fun formatQuery_givenSingleLineQuery_formatsToMultiLineIndentedSyntax() {
        val rawQuery = "query SectionsData(\$ids: [ID!]!, \$partner: String!) { sections(ids: \$ids, partner: \$partner) { id title } }"

        val formatted = formatter.formatQuery(rawQuery)

        assertTrue(formatted.contains("query SectionsData"))
        assertTrue(formatted.contains("sections(ids: \$ids, partner: \$partner)"))
        assertTrue(formatted.contains("    id"))
        assertTrue(formatted.contains("    title"))
    }

    @Test
    fun formatQuery_givenMalformedQuery_returnsRawTrimmedTextWithoutCrashing() {
        val malformedQuery = "query { unclosed_brace"

        val result = formatter.formatQuery(malformedQuery)

        assertEquals("query { unclosed_brace", result)
    }

    @Test
    fun formatQuery_givenEmptyString_returnsEmptyString() {
        assertEquals("", formatter.formatQuery("   "))
    }
}
