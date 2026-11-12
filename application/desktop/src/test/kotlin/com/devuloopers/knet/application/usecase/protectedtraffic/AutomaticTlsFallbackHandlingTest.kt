package com.devuloopers.knet.application.usecase.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Automatic transport decisions never become permanent rules or override explicit user handling. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AutomaticTlsFallbackHandlingTest {
    private fun ProtectionFixture.learned(): AutomaticTlsFallbackKey {
        val session = CaptureSessionId("capture-a")
        context = context!!.copy(sessionId = session)
        val key = automaticTlsFallbackKey(session, tlsFallbackClientScope(context!!.ingress, context!!.downstreamHost),
            captured.serverName!!, 443, null)!!
        fallbackState.value = AutomaticTlsFallbackState(session, 1, setOf(key))
        return key
    }

    @Test fun `current automatic handling updates live while captured rejection stays immutable`() = runTest {
        val fixture = ProtectionFixture(); val key = fixture.learned()
        val values = mutableListOf<ProtectedTrafficFlowHandling?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository, fixture.fallback)(fixture.captured.id).toList(values)
        }
        assertEquals(ProtectedTrafficDecisionEvidence.AutomaticFallback, values.last()!!.decision.evidence)
        assertEquals(ProtectedTrafficAction.TUNNEL, values.last()!!.decision.action)
        fixture.fallback.clear()
        assertEquals(ProtectedTrafficAction.INSPECT, values.last()!!.decision.action)
        assertEquals(InspectionFailureReason.CERTIFICATE_REJECTED, fixture.captured.inspectionFailureReason)
        assertTrue(fixture.repository.configuration.value.rules.isEmpty())
        assertEquals(1, fixture.reads)
        fixture.fallbackState.value = fixture.fallbackState.value.copy(keys = setOf(key))
        fixture.repository.setDefaultAction(ProtectedTrafficAction.INSPECT, false)
        assertEquals(ProtectedTrafficAction.INSPECT, values.last()!!.decision.action)
        assertFalse(values.last()!!.automaticFallbackEnabled)
    }

    @Test fun `inspect tunnel and block choices persist and use default removes only the exact override`() = runTest {
        val fixture = ProtectionFixture(); fixture.learned()
        val set = SetProtectedTrafficFlowActionUseCase(fixture.query, fixture.repository, fixture.fallback)
        val observe = ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository, fixture.fallback)
        for (action in listOf(ProtectedTrafficAction.INSPECT, ProtectedTrafficAction.TUNNEL, ProtectedTrafficAction.BLOCK)) {
            assertIs<SetProtectedTrafficFlowActionResult.Saved>(set.execute(fixture.captured.id, action, ProtectedTrafficQuickRuleScope.DESTINATION))
            assertTrue(fixture.fallback.state.value.keys.isEmpty())
            assertEquals(action, observe(fixture.captured.id).first()!!.decision.action)
            assertEquals(1, fixture.repository.configuration.value.rules.size)
        }
        val unrelated = ProtectedTrafficRule(ProtectedTrafficRuleId("unrelated"), action = ProtectedTrafficAction.BLOCK,
            destination = ProtectedDestinationSelector.parse("other.example.com"))
        fixture.repository.upsertRule(unrelated)
        val reset = ResetProtectedTrafficFlowActionUseCase(fixture.query, fixture.repository, fixture.fallback)
        assertIs<SetProtectedTrafficFlowActionResult.DefaultRestored>(reset.execute(fixture.captured.id))
        val restored = observe(fixture.captured.id).first()!!
        assertNull(restored.destinationRule)
        assertEquals(ProtectedTrafficDecisionEvidence.GlobalDefault, restored.decision.evidence)
        assertTrue(restored.automaticFallbackEnabled)
        assertEquals(listOf(unrelated), fixture.repository.configuration.value.rules)
        assertEquals(OpaqueFlowPolicyAction.INSPECT, fixture.captured.policyAction)
    }

    @Test fun `historical or different device session application and port cannot inherit a temporary decision`() = runTest {
        val fixture = ProtectionFixture(); fixture.learned()
        val observe = ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository, fixture.fallback)
        val originalContext = fixture.context!!
        fixture.context = originalContext.copy(sessionId = CaptureSessionId("old"))
        assertEquals(ProtectedTrafficAction.INSPECT, observe(fixture.captured.id).first()!!.decision.action)
        fixture.context = originalContext.copy(ingress = IngressContext(IngressKind.LanPairedDevice, ClientIdentity("other-device")))
        assertEquals(ProtectedTrafficAction.INSPECT, observe(fixture.captured.id).first()!!.decision.action)
        fixture.context = originalContext
        fixture.record = fixture.captured.copy(sourceApplicationId = "com.example.other")
        assertEquals(ProtectedTrafficAction.INSPECT, observe(fixture.captured.id).first()!!.decision.action)
        fixture.record = fixture.captured.copy(transport = OpaqueTransportProtocol.UDP, security = OpaqueSecurityProtocol.QUIC)
        assertEquals(ProtectedTrafficAction.INSPECT, observe(fixture.captured.id).first()!!.decision.action)
        fixture.record = fixture.captured.copy(destination = TrafficEndpoint("2001:db8::10", 8443))
        assertEquals(ProtectedTrafficAction.INSPECT, observe(fixture.captured.id).first()!!.decision.action)
    }

    @Test fun `global choice is atomic and clearing an override requires valid retained destination evidence`() = runTest {
        val fixture = ProtectionFixture(); fixture.learned()
        val set = SetProtectedTrafficDefaultActionUseCase(fixture.repository, fixture.fallback)
        for (handling in ProtectedTrafficDefaultHandling.entries) {
            set(handling)
            val configuration = fixture.repository.configuration.value
            assertEquals(handling.action, configuration.defaultAction)
            assertEquals(handling.automaticFallback, configuration.automaticTlsFallbackEnabled)
            assertEquals(handling, configuration.defaultHandling)
            assertTrue(fixture.fallback.state.value.keys.isEmpty())
        }
        fixture.record = null
        assertIs<SetProtectedTrafficFlowActionResult.MissingFlow>(ResetProtectedTrafficFlowActionUseCase(
            fixture.query, fixture.repository, fixture.fallback).execute(fixture.captured.id))
    }
}
