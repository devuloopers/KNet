package com.devuloopers.knet.engine.interceptor

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BreakpointMatcherTest {

    @BeforeTest
    fun setUp() {
        BreakpointRuleRegistry.clearRules()
    }

    @Test
    fun testFindMatchingRulesForPhases() {
        val reqRule = BreakpointRule("req1", ".*api.*", "GET", BreakpointPhase.REQUEST)
        val resRule = BreakpointRule("res1", ".*api.*", "GET", BreakpointPhase.RESPONSE)
        BreakpointRuleRegistry.addRule(reqRule)
        BreakpointRuleRegistry.addRule(resRule)

        val matchedReq = BreakpointMatcher.findMatchingRequestRule("https://api.test.com/data", "GET")
        assertNotNull(matchedReq)
        assertEquals("req1", matchedReq.id)

        val matchedRes = BreakpointMatcher.findMatchingResponseRule("https://api.test.com/data", "GET")
        assertNotNull(matchedRes)
        assertEquals("res1", matchedRes.id)

        val nonMatchedReq = BreakpointMatcher.findMatchingRequestRule("https://other.com/data", "GET")
        assertNull(nonMatchedReq)
    }
}
