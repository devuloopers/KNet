package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.RuleModel
import kotlin.system.measureTimeMillis
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    @BeforeTest
    fun setUp() {
        BreakpointRuleRegistry.clearRules()
        InterceptSessionManager.clearSuspensions()
    }

    @Test
    fun testBreakpointMatcherPerformanceWithMultipleRules() {
        repeat(100) { i ->
            BreakpointRuleRegistry.addRule(
                RuleModel("b-$i", "b-$i", BreakpointPhase.REQUEST, ".*api-$i\\.example\\.com.*", "GET")
            )
        }

        val duration = measureTimeMillis {
            repeat(1000) {
                BreakpointMatcher.findMatchingRequestRule("https://api-50.example.com/data", "GET")
            }
        }

        assertTrue(duration < 1000, "1000 rule evaluations against 100 rules must complete within 1 second")
    }
}
