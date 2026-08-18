package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying properties of the canonical mTLS rule specification.
 */
class MtlsRuleSpecTest {

    /**
     * Verifies that the canonical mTLS fields are set correctly by the constructor.
     *
     * Design Intent: Configuration for mutual TLS client certificate matching is based on this rule entity.
     */
    @Test
    fun testMtlsRuleProperties() {
        val rule = MtlsRuleSpec(
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
