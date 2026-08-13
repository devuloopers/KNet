package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.RuleType
import com.devuloopers.knet.domain.rules.model.matchesTransaction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakpointRuleTest {

    @Test
    fun testBreakpointRuleMatching() {
        val rule = RuleModel(
            id = "b1",
            name = "b1",
            condition = ".*api\\.example\\.com.*",
            action = "POST",
            type = RuleType.REQUEST
        )

        assertTrue(rule.matchesTransaction("https://api.example.com/v1/users", "POST", RuleType.REQUEST))
        assertFalse(rule.matchesTransaction("https://api.example.com/v1/users", "GET", RuleType.REQUEST), "Method mismatch must fail")
        assertFalse(rule.matchesTransaction("https://api.example.com/v1/users", "POST", RuleType.RESPONSE), "Phase mismatch must fail")
    }

    @Test
    fun testBothPhasesMatching() {
        val rule = RuleModel(
            id = "b2",
            name = "b2",
            condition = "*",
            action = "ALL",
            type = RuleType.BOTH
        )

        assertTrue(rule.matchesTransaction("https://any.com/test", "GET", RuleType.REQUEST))
        assertTrue(rule.matchesTransaction("https://any.com/test", "POST", RuleType.RESPONSE))
    }

    @Test
    fun testDisabledRuleRejection() {
        val rule = RuleModel(
            id = "b3",
            name = "b3",
            condition = "*",
            enabled = false
        )

        assertFalse(rule.matchesTransaction("https://test.com", "GET", RuleType.REQUEST))
    }
}
