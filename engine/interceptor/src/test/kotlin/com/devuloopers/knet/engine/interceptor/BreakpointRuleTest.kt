package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.BreakpointTransportMatcher
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakpointRuleTest {

    @Test
    fun testBreakpointRuleMatching() {
        val rule = BreakpointRule(
            id = "b1",
            name = "b1",
            urlPattern = ".*api\\.example\\.com.*",
            method = HttpMethod.POST,
            phase = BreakpointPhase.REQUEST
        )

        val matcher = BreakpointTransportMatcher(rule)
        assertTrue(matcher.matches("https://api.example.com/v1/users", "POST", BreakpointPhase.REQUEST))
        assertFalse(matcher.matches("https://api.example.com/v1/users", "GET", BreakpointPhase.REQUEST), "Method mismatch must fail")
        assertFalse(matcher.matches("https://api.example.com/v1/users", "POST", BreakpointPhase.RESPONSE), "Phase mismatch must fail")
    }

    @Test
    fun testBothPhasesMatching() {
        val rule = BreakpointRule(
            id = "b2",
            name = "b2",
            urlPattern = "*",
            phase = BreakpointPhase.BOTH
        )

        val matcher = BreakpointTransportMatcher(rule)
        assertTrue(matcher.matches("https://any.com/test", "GET", BreakpointPhase.REQUEST))
        assertTrue(matcher.matches("https://any.com/test", "POST", BreakpointPhase.RESPONSE))
    }

    @Test
    fun testDisabledRuleRejection() {
        val rule = BreakpointRule(
            id = "b3",
            name = "b3",
            urlPattern = "*",
            enabled = false
        )

        assertFalse(
            BreakpointTransportMatcher(rule).matches(
                "https://test.com",
                "GET",
                BreakpointPhase.REQUEST,
            ),
        )
    }
}
