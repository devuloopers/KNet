package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicyAction
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectedTrafficTlsInterceptionPolicyTest {
    @Test
    fun `desktop tunnel retains original bypass action evidence`() {
        val configuration = ProtectedTrafficConfiguration(
            rules = listOf(
                ProtectedTrafficRule(
                    id = ProtectedTrafficRuleId("bypass-api"),
                    action = ProtectedTrafficAction.BYPASS,
                    destination = ProtectedDestinationSelector.parse("api.example"),
                ),
            ),
        )
        val policy = ProtectedTrafficTlsInterceptionPolicy({ configuration })

        val decision = policy.decide(TlsInterceptionRequest("api.example", 443))

        assertEquals(TlsInterceptionMode.TUNNEL, decision.mode)
        assertEquals(TlsInterceptionPolicyAction.BYPASS, decision.policyAction)
        assertEquals("bypass-api", decision.ruleId?.value)
    }
}
