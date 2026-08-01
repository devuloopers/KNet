package com.devuloopers.knet.engine.interceptor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakpointRuleTest {

    @Test
    fun testBreakpointRuleMatching() {
        val rule = BreakpointRule(
            id = "b1",
            urlPattern = ".*api\\.example\\.com.*",
            method = "POST",
            phase = BreakpointPhase.REQUEST,
            priority = 0
        )

        assertTrue(rule.matches("https://api.example.com/v1/users", "POST", BreakpointPhase.REQUEST))
        assertFalse(rule.matches("https://api.example.com/v1/users", "GET", BreakpointPhase.REQUEST), "Method mismatch must fail")
        assertFalse(rule.matches("https://api.example.com/v1/users", "POST", BreakpointPhase.RESPONSE), "Phase mismatch must fail")
    }

    @Test
    fun testBothPhasesMatching() {
        val rule = BreakpointRule(
            id = "b2",
            urlPattern = ".*",
            method = null,
            phase = BreakpointPhase.BOTH
        )

        assertTrue(rule.matches("https://any.com/test", "GET", BreakpointPhase.REQUEST))
        assertTrue(rule.matches("https://any.com/test", "POST", BreakpointPhase.RESPONSE))
    }

    @Test
    fun testDisabledRuleRejection() {
        val rule = BreakpointRule(
            id = "b3",
            urlPattern = ".*",
            enabled = false
        )

        assertFalse(rule.matches("https://test.com", "GET", BreakpointPhase.REQUEST))
    }
}
