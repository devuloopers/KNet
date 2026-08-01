package com.devuloopers.knet.engine.interceptor

import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val rule = BreakpointRule("b1", ".*", "GET")
        BreakpointRuleRegistry.addRule(rule)
        assertNotNull(BreakpointRuleRegistry.getRules())

        val req = TestFixtures.createHttpRequestDto()
        val event = InterceptSessionManager.suspendRequest(req)
        assertNotNull(event)
        InterceptSessionManager.clearSuspensions()
    }
}
