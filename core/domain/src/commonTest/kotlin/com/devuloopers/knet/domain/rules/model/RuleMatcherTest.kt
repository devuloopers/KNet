package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleMatcherTest {

    @Test
    fun testDefaultHttpRuleMatching() {
        val rule = RuleModel(
            name = "Test HTTP Rule",
            condition = "https://api.example.com/v1/users",
            action = "GET",
            enabled = true,
            protocolCriteria = ProtocolMatchCriteria.HttpDefault
        )

        assertTrue(rule.matchesTransaction("https://api.example.com/v1/users", "GET"))
        assertFalse(rule.matchesTransaction("https://api.example.com/v1/users", "POST"))
        assertFalse(rule.matchesTransaction("https://other.example.com", "GET"))
    }

    @Test
    fun testWildcardStarRuleMatching() {
        val rule = RuleModel(
            name = "Star Wildcard Rule",
            condition = "*",
            action = "ALL",
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
        val rule = RuleModel(
            name = "FormattedQuotes Rule",
            condition = "https://stg-04astra.cnbc.com/graphql",
            action = "POST",
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
        val rule = RuleModel(
            name = "All GraphQL Rule",
            condition = "/graphql",
            action = "POST",
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
