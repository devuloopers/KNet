package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakpointTransportMatcherTest {
    @Test
    fun `portless URL pattern matches default port through an exact typed criterion`() {
        val matcher = BreakpointTransportMatcher(
            BreakpointRule(
                id = "default-https",
                urlPattern = "https://api.example.com/v1/users",
                portCriteria = BreakpointPortCriteria.Exact(443),
            ),
        )
        val target = BreakpointTransportTarget(
            canonicalUrl = "https://api.example.com:443/v1/users",
            portlessUrl = "https://api.example.com/v1/users",
            port = 443,
        )

        assertTrue(matcher.matches(target, "GET", BreakpointPhase.REQUEST))
    }

    @Test
    fun `exact port criterion rejects another destination port`() {
        val matcher = BreakpointTransportMatcher(
            BreakpointRule(
                id = "custom-port",
                urlPattern = "https://api.example.com/*",
                portCriteria = BreakpointPortCriteria.Exact(8443),
            ),
        )
        val target = BreakpointTransportTarget(
            canonicalUrl = "https://api.example.com:443/v1/users",
            portlessUrl = "https://api.example.com/v1/users",
            port = 443,
        )

        assertFalse(matcher.matches(target, "GET", BreakpointPhase.REQUEST))
    }

    @Test
    fun `existing explicit-port URL expressions remain valid`() {
        val matcher = BreakpointTransportMatcher(
            BreakpointRule(id = "explicit-port", urlPattern = "https://api.example.com:8443/*"),
        )
        val target = BreakpointTransportTarget(
            canonicalUrl = "https://api.example.com:8443/v1/users",
            portlessUrl = "https://api.example.com/v1/users",
            port = 8443,
        )

        assertTrue(matcher.matches(target, "GET", BreakpointPhase.REQUEST))
    }

    @Test
    fun `matches typed method phase and exact URL`() {
        val matcher = BreakpointTransportMatcher(
            BreakpointRule(
                id = "http-rule",
                urlPattern = "https://api.example.com/v1/users",
                method = HttpMethod.GET,
                phase = BreakpointPhase.REQUEST,
            ),
        )

        assertTrue(matcher.matches("https://api.example.com/v1/users", "GET", BreakpointPhase.REQUEST))
        assertFalse(matcher.matches("https://api.example.com/v1/users", "POST", BreakpointPhase.REQUEST))
        assertFalse(matcher.matches("https://api.example.com/v1/users", "GET", BreakpointPhase.RESPONSE))
        assertFalse(matcher.matches("https://other.example.com", "GET", BreakpointPhase.REQUEST))
    }

    @Test
    fun `compiles wildcard and regex patterns once`() {
        val wildcard = BreakpointTransportMatcher(
            BreakpointRule(id = "wildcard", urlPattern = "https://*.example.com/*"),
        )
        val regex = BreakpointTransportMatcher(
            BreakpointRule(id = "regex", urlPattern = ".*api\\.example\\.com.*"),
        )

        assertTrue(wildcard.matches("https://api.example.com/users", "GET", BreakpointPhase.REQUEST))
        assertTrue(regex.matches("https://api.example.com/users", "POST", BreakpointPhase.RESPONSE))
    }

    @Test
    fun `disabled rule never matches`() {
        val matcher = BreakpointTransportMatcher(
            BreakpointRule(id = "disabled", enabled = false),
        )

        assertFalse(matcher.matches("https://example.com", "GET", BreakpointPhase.REQUEST))
    }
}
