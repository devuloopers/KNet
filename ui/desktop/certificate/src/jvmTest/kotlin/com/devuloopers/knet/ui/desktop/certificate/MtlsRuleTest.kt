package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.model.MtlsRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying properties and structure of the [MtlsRule] presentation model.
 */
public class MtlsRuleTest {

    /**
     * Verifies that the [MtlsRule] fields are set correctly by the constructor.
     *
     * Design Intent: Configuration for mutual TLS client certificate matching is based on this rule entity.
     */
    @Test
    public fun testMtlsRuleProperties() {
        val rule = MtlsRule(
            ruleName = "production-rule",
            hostPattern = "*.prod.devuloopers.com",
            certificateAlias = "prod-cert",
            enabled = true
        )
        assertEquals("production-rule", rule.ruleName)
        assertEquals("*.prod.devuloopers.com", rule.hostPattern)
        assertEquals("prod-cert", rule.certificateAlias)
        assertTrue(rule.enabled)
    }
}
