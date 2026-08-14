package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.RuleModel
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
        val reqRule = RuleModel(id = "req1", name = "req1", condition = ".*api.*", action = "GET", type = BreakpointPhase.REQUEST)
        val resRule = RuleModel(id = "res1", name = "res1", condition = ".*api.*", action = "GET", type = BreakpointPhase.RESPONSE)
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

    @Test
    fun testWildcardStarMatching() {
        val starRule = RuleModel(id = "star1", name = "star1", condition = "*formattedQuotes*", action = "ALL", type = BreakpointPhase.BOTH)
        BreakpointRuleRegistry.addRule(starRule)

        val matched = BreakpointMatcher.findMatchingRequestRule("https://api.site.com/v1/formattedQuotes/query", "POST")
        assertNotNull(matched)
        assertEquals("star1", matched.id)
    }
}
