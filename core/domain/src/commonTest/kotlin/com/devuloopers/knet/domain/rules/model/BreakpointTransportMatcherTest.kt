package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakpointTransportMatcherTest {
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
