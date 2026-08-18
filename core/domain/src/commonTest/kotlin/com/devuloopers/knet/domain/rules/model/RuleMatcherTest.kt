package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleMatcherTest {

    @Test
    fun testDefaultHttpRuleMatching() {
        val rule = BreakpointRule(
            id = "http-rule",
            name = "Test HTTP Rule",
            urlPattern = "https://api.example.com/v1/users",
            method = com.devuloopers.knet.traffic.model.http.HttpMethod.GET,
            enabled = true,
            protocolCriteria = ProtocolMatchCriteria.HttpDefault
        )

        assertTrue(rule.matchesTransaction("https://api.example.com/v1/users", "GET"))
        assertFalse(rule.matchesTransaction("https://api.example.com/v1/users", "POST"))
        assertFalse(rule.matchesTransaction("https://other.example.com", "GET"))
    }

    @Test
    fun testWildcardStarRuleMatching() {
        val rule = BreakpointRule(
            id = "wildcard-rule",
            name = "Star Wildcard Rule",
            urlPattern = "*",
            enabled = true,
            protocolCriteria = ProtocolMatchCriteria.GraphQL(operationName = "FormattedQuotes")
        )

        val matchingMeta = InterceptionMetadata.GraphQL(
            operationName = "FormattedQuotes",
            operationType = "Query",
            querySummary = "query FormattedQuotes..."
        )

        assertTrue(
            rule.matchesTransaction(
                url = "https://stg-04astra.cnbc.com/graphql",
                method = "POST",
                metadata = matchingMeta
            ),
            "Star wildcard rule must match any URL"
        )
    }

    @Test
    fun testGraphQLRuleMatchingWithSpecificOperationName() {
        val rule = BreakpointRule(
            id = "graphql-specific-rule",
            name = "FormattedQuotes Rule",
            urlPattern = "https://stg-04astra.cnbc.com/graphql",
            method = com.devuloopers.knet.traffic.model.http.HttpMethod.POST,
            enabled = true,
            protocolCriteria = ProtocolMatchCriteria.GraphQL(operationName = "FormattedQuotes")
        )

        val matchingMeta = InterceptionMetadata.GraphQL(
            operationName = "FormattedQuotes",
            operationType = "Query",
            querySummary = "query FormattedQuotes..."
        )

        val nonMatchingMeta = InterceptionMetadata.GraphQL(
            operationName = "SectionsData",
            operationType = "Query",
            querySummary = "query SectionsData..."
        )

        assertTrue(
            rule.matchesTransaction(
                url = "https://stg-04astra.cnbc.com/graphql",
                method = "POST",
                metadata = matchingMeta
            )
        )

        assertFalse(
            rule.matchesTransaction(
                url = "https://stg-04astra.cnbc.com/graphql",
                method = "POST",
                metadata = nonMatchingMeta
            )
        )
    }

    @Test
    fun testGraphQLRuleMatchingWithBlankOperationNameMatchesAnyGraphQL() {
        val rule = BreakpointRule(
            id = "graphql-all-rule",
            name = "All GraphQL Rule",
            urlPattern = "/graphql",
            method = com.devuloopers.knet.traffic.model.http.HttpMethod.POST,
            enabled = true,
            protocolCriteria = ProtocolMatchCriteria.GraphQL(operationName = null)
        )

        val meta = InterceptionMetadata.GraphQL(
            operationName = "SectionsData",
            operationType = "Query",
            querySummary = "query SectionsData..."
        )

        assertTrue(
            rule.matchesTransaction(
                url = "https://stg-04astra.cnbc.com/graphql",
                method = "POST",
                metadata = meta
            )
        )
    }
}
