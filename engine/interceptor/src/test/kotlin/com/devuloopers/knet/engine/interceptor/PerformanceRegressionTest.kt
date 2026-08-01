package com.devuloopers.knet.engine.interceptor

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
                BreakpointRule("b-$i", ".*api-$i\\.example\\.com.*", "GET", BreakpointPhase.REQUEST, priority = i)
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
