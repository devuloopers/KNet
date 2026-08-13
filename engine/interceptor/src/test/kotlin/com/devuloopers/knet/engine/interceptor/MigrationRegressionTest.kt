package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.RuleModel
import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val rule = RuleModel("b1", "b1", condition = "*", action = "GET")
        BreakpointRuleRegistry.addRule(rule)
        assertNotNull(BreakpointRuleRegistry.getRules())

        val req = TestFixtures.createHttpRequestDto()
        val event = InterceptSessionManager.suspendRequest(req)
        assertNotNull(event)
        InterceptSessionManager.clearSuspensions()
    }
}
