package com.devuloopers.knet.engine.formatter.graphql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphQLQuerySynchronizerTest {

    @Test
    fun testExtractOperationNameFromNamedQuery() {
        val query = """
            query GetUserProfile(${'$'}id: ID!) {
              user(id: ${'$'}id) {
                name
                email
              }
            }
        """.trimIndent()

        val extracted = GraphQLQuerySynchronizer.extractOperationName(query)
        assertEquals("GetUserProfile", extracted)
    }

    @Test
    fun testExtractOperationNameFromMutationAndSubscription() {
        val mutation = """
            mutation CreateComment(${'$'}input: CommentInput!) {
              addComment(input: ${'$'}input) { id }
            }
        """.trimIndent()
        assertEquals("CreateComment", GraphQLQuerySynchronizer.extractOperationName(mutation))

        val subscription = """
            subscription OnLiveScore {
              liveScore { score }
            }
        """.trimIndent()
        assertEquals("OnLiveScore", GraphQLQuerySynchronizer.extractOperationName(subscription))
    }

    @Test
    fun testExtractOperationNameFromAnonymousQueriesReturnsNull() {
        val bareQuery = """
            {
              viewer {
                login
              }
            }
        """.trimIndent()
        assertNull(GraphQLQuerySynchronizer.extractOperationName(bareQuery))

        val keywordAnonymous = """
            query {
              viewer {
                login
              }
            }
        """.trimIndent()
        assertNull(GraphQLQuerySynchronizer.extractOperationName(keywordAnonymous))
    }

    @Test
    fun testExtractOperationNameWithLeadingComments() {
        val queryWithComments = """
            # This is a leading comment
            # Another metadata note
            query FormattedQuotes(${'$'}symbols: [String]) {
              formattedQuotes(symbols: ${'$'}symbols) {
                symbol
                price
              }
            }
        """.trimIndent()

        assertEquals("FormattedQuotes", GraphQLQuerySynchronizer.extractOperationName(queryWithComments))
    }

    @Test
    fun testUpdateOperationNameRenamesExistingNamedQuery() {
        val query = """
            query FormattedQuotes(${'$'}symbols: [String]) {
              formattedQuotes(symbols: ${'$'}symbols) {
                symbol
              }
            }
        """.trimIndent()

        val updated = GraphQLQuerySynchronizer.updateOperationName(query, "MarketQuotes")
        assertTrue(updated.startsWith("query MarketQuotes(\$symbols: [String]) {"))
        assertEquals("MarketQuotes", GraphQLQuerySynchronizer.extractOperationName(updated))
    }

    @Test
    fun testUpdateOperationNameOnBareAnonymousQuery() {
        val bareQuery = """
            {
              quotes {
                symbol
              }
            }
        """.trimIndent()

        val updated = GraphQLQuerySynchronizer.updateOperationName(bareQuery, "GetQuotes")
        assertTrue(updated.startsWith("query GetQuotes {"))
        assertEquals("GetQuotes", GraphQLQuerySynchronizer.extractOperationName(updated))
    }

    @Test
    fun testUpdateOperationNameOnKeywordAnonymousQuery() {
        val anonQuery = """
            query(${'$'}id: ID!) {
              user(id: ${'$'}id) {
                name
              }
            }
        """.trimIndent()

        val updated = GraphQLQuerySynchronizer.updateOperationName(anonQuery, "FetchUser")
        assertTrue(updated.startsWith("query FetchUser(\$id: ID!) {"))
        assertEquals("FetchUser", GraphQLQuerySynchronizer.extractOperationName(updated))
    }

    @Test
    fun testUpdateOperationNameToBlankClearsName() {
        val namedQuery = """
            query GetUser {
              user { id }
            }
        """.trimIndent()

        val updated = GraphQLQuerySynchronizer.updateOperationName(namedQuery, "")
        assertTrue(updated.startsWith("query {"))
        assertNull(GraphQLQuerySynchronizer.extractOperationName(updated))
    }

    @Test
    fun testUpdateOperationNameOnEmptyQueryGeneratesTemplate() {
        val updated = GraphQLQuerySynchronizer.updateOperationName("", "NewQuery")
        assertTrue(updated.contains("query NewQuery {"))
        assertEquals("NewQuery", GraphQLQuerySynchronizer.extractOperationName(updated))
    }
}
