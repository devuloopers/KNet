package com.devuloopers.knet.application.usecase.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Read-only scope matching and live policy changes cannot rewrite historical capture evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveProtectedTrafficFlowHandlingUseCaseTest {
    @Test fun `invalid retained target metadata cannot throw during immediate presentation`() = runTest {
        val fixture = ProtectionFixture()
        fixture.repository.setDefaultAction(ProtectedTrafficAction.TUNNEL)
        val useCase = ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository)
        assertNull(useCase.currentManualHandling(fixture.captured.copy(serverName = "https://invalid.test/path"),
            com.devuloopers.knet.application.contract.traffic.TrafficSource.Other))
        assertNull(useCase.currentManualHandling(fixture.captured.copy(destination = TrafficEndpoint("127.0.0.1", null)),
            com.devuloopers.knet.application.contract.traffic.TrafficSource.Other))
    }

    @Test fun `manual presentation is immediate read only and never guesses automatic fallback or mixed source identity`() = runTest {
        val fixture = ProtectionFixture()
        val useCase = ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository, fixture.fallback)
        val source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other
        assertNull(useCase.currentManualHandling(fixture.captured, source), "Learned fallback needs canonical connection/session metadata")
        val exact = ProtectedTrafficRule(ProtectedTrafficRuleId("saved"), action = ProtectedTrafficAction.TUNNEL,
            destination = ProtectedDestinationSelector.parse(fixture.captured.serverName!!), port = 443, transport = ProtectedTransportProtocol.TCP)
        fixture.repository.upsertRule(exact)
        val preview = assertNotNull(useCase.currentManualHandling(fixture.captured, source))
        assertEquals(exact, preview.destinationRule)
        assertEquals(ProtectedTrafficAction.TUNNEL, preview.decision.action)
        assertEquals(0, fixture.reads)
        assertEquals(1, fixture.writes, "Reading a policy preview must not create or mutate a rule")
        assertNull(useCase.currentManualHandling(fixture.captured, com.devuloopers.knet.application.contract.traffic.TrafficSource.All))
        fixture.repository.configuration.value = fixture.repository.configuration.value.copy(configurationReady = false)
        assertNull(useCase.currentManualHandling(fixture.captured, source))
    }

    @Test fun `policy stays live during a delayed canonical lookup with no loading gap between preview and resolved handling`() = runTest {
        val fixture = ProtectionFixture()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fixture.beforeRead = { gate.await() }
        val exact = ProtectedTrafficRule(ProtectedTrafficRuleId("saved"), action = ProtectedTrafficAction.TUNNEL,
            destination = ProtectedDestinationSelector.parse(fixture.captured.serverName!!), port = 443, transport = ProtectedTransportProtocol.TCP)
        fixture.repository.upsertRule(exact)
        val useCase = ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository)
        val values = mutableListOf<ProtectedTrafficFlowHandling?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase.observeSelection(fixture.captured, com.devuloopers.knet.application.contract.traffic.TrafficSource.Other).toList(values)
        }
        runCurrent()
        assertEquals(ProtectedTrafficAction.TUNNEL, values.last()?.decision?.action)
        fixture.repository.upsertRule(exact.copy(action = ProtectedTrafficAction.INSPECT))
        runCurrent()
        assertEquals(ProtectedTrafficAction.INSPECT, values.last()?.decision?.action)
        gate.complete(Unit); runCurrent()
        assertEquals(ProtectedTrafficAction.INSPECT, values.last()?.decision?.action)
        assertTrue(values.none { it == null }, "A resolved manual choice must never flash through Loading/default")
        assertEquals(1, fixture.reads)
    }

    @Test fun `missing canonical history supersedes a preview and later rule updates cannot resurrect it`() = runTest {
        val fixture = ProtectionFixture()
        fixture.repository.setDefaultAction(ProtectedTrafficAction.TUNNEL)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fixture.beforeRead = { gate.await() }
        val values = mutableListOf<ProtectedTrafficFlowHandling?>()
        val useCase = ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, fixture.repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase.observeSelection(fixture.captured, com.devuloopers.knet.application.contract.traffic.TrafficSource.Other).toList(values)
        }
        runCurrent(); assertNotNull(values.last())
        fixture.record = null; gate.complete(Unit); runCurrent()
        assertNull(values.last())
        fixture.repository.setDefaultAction(ProtectedTrafficAction.BLOCK); runCurrent()
        assertNull(values.last())
    }

    @Test fun `same hostname policy previews remain separated by full device identity and canonical ingress wins`() = runTest {
        val fixture = ProtectionFixture()
        val first = com.devuloopers.knet.domain.policy.PolicyWorkspace.Device(com.devuloopers.knet.identity.RegisteredDeviceId("first"))
        val second = com.devuloopers.knet.domain.policy.PolicyWorkspace.Device(com.devuloopers.knet.identity.RegisteredDeviceId("second"))
        val repository = object : com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository by fixture.repository {
            override fun forWorkspace(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace): com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository =
                object : com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository by fixture.repository {
                    override val configuration = MutableStateFlow(ProtectedTrafficConfiguration(defaultAction =
                        if (workspace == first) ProtectedTrafficAction.TUNNEL else ProtectedTrafficAction.BLOCK))
                }
        }
        val useCase = ObserveProtectedTrafficFlowHandlingUseCase(fixture.query, repository)
        assertEquals(ProtectedTrafficAction.TUNNEL, useCase.currentManualHandling(fixture.captured,
            com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(first.id))?.decision?.action)
        assertEquals(ProtectedTrafficAction.BLOCK, useCase.currentManualHandling(fixture.captured,
            com.devuloopers.knet.application.contract.traffic.TrafficSource.Device(second.id))?.decision?.action)
        fixture.context = fixture.context!!.copy(ingress = IngressContext(IngressKind.LanPairedDevice, ClientIdentity(second.id.value)))
        assertEquals(ProtectedTrafficAction.BLOCK, useCase(fixture.captured.id).first()?.decision?.action)
    }

    @Test fun `IPv6 flow with SNI follows saved policy without changing historical rejection`()=runTest {
        val fixture=ProtectionFixture()
        val values=mutableListOf<ProtectedTrafficFlowHandling?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveProtectedTrafficFlowHandlingUseCase(fixture.query,fixture.repository)(fixture.captured.id).toList(values)
        }
        assertEquals(ProtectedTrafficAction.INSPECT,values.last()!!.decision.action)
        fixture.repository.upsertRule(ProtectedTrafficRule(ProtectedTrafficRuleId("manual"),action=ProtectedTrafficAction.TUNNEL,
            destination=ProtectedDestinationSelector.parse("play-fe.googleapis.com"),port=443))
        assertEquals(ProtectedTrafficAction.TUNNEL,values.last()!!.decision.action)
        assertEquals(OpaqueFlowPolicyAction.INSPECT,fixture.captured.policyAction)
        assertEquals(InspectionFailureReason.CERTIFICATE_REJECTED,fixture.captured.inspectionFailureReason)
        assertEquals(1,fixture.reads,"Policy emissions must not repeatedly query captured history")
    }

    @Test fun `observing failed connections never creates an automatic exception`()=runTest {
        val fixture=ProtectionFixture()
        val useCase=ObserveProtectedTrafficFlowHandlingUseCase(fixture.query,fixture.repository)
        repeat(10) { index ->
            val handling=useCase(com.devuloopers.knet.traffic.id.OpaqueFlowId("failure-$index")).first()!!
            assertEquals(ProtectedTrafficAction.INSPECT,handling.decision.action)
            assertEquals(ProtectedTrafficDecisionEvidence.GlobalDefault,handling.decision.evidence)
        }
        assertTrue(fixture.repository.configuration.value.rules.isEmpty())
        assertEquals(0,fixture.writes)
    }

    @Test fun `missing history and missing ports stay unavailable and readiness is authoritative`()=runTest {
        val fixture=ProtectionFixture()
        val useCase=ObserveProtectedTrafficFlowHandlingUseCase(fixture.query,fixture.repository)
        fixture.record=null
        assertNull(useCase(fixture.captured.id).first())
        fixture.record=fixture.captured.copy(destination=TrafficEndpoint("2001:db8::10",null))
        assertNull(useCase(fixture.captured.id).first(),"Missing ports must not be guessed as HTTPS")
        fixture.record=fixture.captured
        fixture.repository.configuration.value=ProtectedTrafficConfiguration(configurationReady=false)
        val value=useCase(fixture.captured.id).first()!!
        assertFalse(value.configurationReady)
    }
    @Test fun `destination selection identifies enabled disabled inherited and overridden rules`()=runTest {
        val fixture=ProtectionFixture()
        val useCase=ObserveProtectedTrafficFlowHandlingUseCase(fixture.query,fixture.repository)
        val exact=ProtectedTrafficRule(ProtectedTrafficRuleId("destination"),action=ProtectedTrafficAction.TUNNEL,
            destination=ProtectedDestinationSelector.parse("play-fe.googleapis.com"),port=443,transport=ProtectedTransportProtocol.TCP)
        fixture.repository.upsertRule(exact)
        val selected=useCase(fixture.captured.id).first()!!
        assertEquals(exact,selected.destinationRule);assertEquals(exact,selected.winningRule)
        fixture.repository.upsertRule(exact.copy(enabled=false))
        val disabled=useCase(fixture.captured.id).first()!!
        assertFalse(disabled.destinationRule!!.enabled);assertNull(disabled.winningRule)
        fixture.repository.deleteRule(exact.id)
        fixture.repository.setDefaultAction(ProtectedTrafficAction.TUNNEL)
        assertNull(useCase(fixture.captured.id).first()!!.destinationRule)
        val wildcard=exact.copy(id=ProtectedTrafficRuleId("wildcard"),destination=ProtectedDestinationSelector.parse("*.googleapis.com"),port=null)
        fixture.repository.upsertRule(wildcard)
        val inherited=useCase(fixture.captured.id).first()!!
        assertNull(inherited.destinationRule);assertEquals(wildcard,inherited.winningRule)
        fixture.record=fixture.captured.copy(sourceApplicationId="com.example.billing")
        val application=ProtectedTrafficRule(ProtectedTrafficRuleId("app"),action=ProtectedTrafficAction.INSPECT,
            sourceApplication=ProtectedSourceApplicationId("com.example.billing"))
        fixture.repository.upsertRule(exact);fixture.repository.upsertRule(application)
        val overridden=useCase(fixture.captured.id).first()!!
        assertEquals(exact,overridden.destinationRule);assertEquals(application,overridden.winningRule)
        assertEquals(ProtectedTrafficAction.INSPECT,overridden.decision.action)
        fixture.record=fixture.record!!.copy(sourceApplicationId="com.example.other")
        assertEquals(exact,useCase(fixture.captured.id).first()!!.winningRule,
            "A different application must not inherit the overriding application rule")
    }

    @Test fun `canonical host SNI port and transport select only the equivalent destination rule`()=runTest {
        val fixture=ProtectionFixture()
        val useCase=ObserveProtectedTrafficFlowHandlingUseCase(fixture.query,fixture.repository)
        val exact=ProtectedTrafficRule(ProtectedTrafficRuleId("destination"),action=ProtectedTrafficAction.TUNNEL,
            destination=ProtectedDestinationSelector.parse("PLAY-FE.GOOGLEAPIS.COM"),port=443,transport=ProtectedTransportProtocol.TCP)
        fixture.repository.upsertRule(exact)
        fixture.record=fixture.captured.copy(serverName="PLAY-FE.GOOGLEAPIS.COM")
        assertEquals(exact,useCase(fixture.captured.id).first()!!.destinationRule)
        listOf(fixture.captured.copy(serverName="other.googleapis.com"),
            fixture.captured.copy(destination=TrafficEndpoint("2001:db8::10",8443)),
            fixture.captured.copy(transport=OpaqueTransportProtocol.UDP)).forEach { record ->
            fixture.record=record
            assertNull(useCase(record.id).first()!!.destinationRule)
        }
        fixture.record=fixture.captured.copy(sourceApplicationId="com.example.other")
        assertEquals(exact,useCase(fixture.captured.id).first()!!.destinationRule,
            "An explicit destination rule keeps its existing cross-application scope")
    }

}
