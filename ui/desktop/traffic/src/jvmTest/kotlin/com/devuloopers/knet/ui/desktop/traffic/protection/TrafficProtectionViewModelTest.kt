package com.devuloopers.knet.ui.desktop.traffic.protection

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.OpaqueFlowPolicyAction
import com.devuloopers.knet.ui.desktop.traffic.model.*
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficProtectionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** In-place mutations, failure feedback, duplicate-click protection and selection fences. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficProtectionViewModelTest {
    private fun ProtectionFixture.viewModel()=TrafficProtectionViewModel(
        ObserveProtectedTrafficFlowHandlingUseCase(query,repository,fallback),SetProtectedTrafficFlowActionUseCase(query,repository,fallback), ResetProtectedTrafficFlowActionUseCase(query,repository,fallback))

    @Test fun `equivalent TLS rows keep their live saved handling before and during delayed selection lookup`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = ProtectionFixture()
        fixture.repository.setDefaultAction(ProtectedTrafficAction.TUNNEL)
        val vm = fixture.viewModel(); val store = ViewModelStore().apply { put("vm", vm) }
        val source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other
        try {
            vm.selectCapturedFlow(fixture.captured, source); runCurrent()
            val next = fixture.captured.copy(id = OpaqueFlowId("next"), connectionId = com.devuloopers.knet.traffic.id.ConnectionId("next-connection"))
            val gate = CompletableDeferred<Unit>(); fixture.beforeRead = { gate.await() }
            val firstFrame = vm.stateForSelection(next, source)
            assertEquals(next.id, firstFrame.handling?.flowId)
            assertEquals(ProtectedTrafficAction.TUNNEL, firstFrame.handling?.decision?.action)
            assertFalse(firstFrame.isLoading)
            vm.selectCapturedFlow(next, source)
            assertFalse(vm.uiState.value.isLoading)
            runCurrent(); assertEquals(ProtectedTrafficAction.TUNNEL, vm.uiState.value.handling?.decision?.action)
            fixture.repository.setDefaultAction(ProtectedTrafficAction.BLOCK); runCurrent()
            assertEquals(ProtectedTrafficAction.BLOCK, vm.uiState.value.handling?.decision?.action)
            gate.complete(Unit); runCurrent()
            assertEquals(next.id, vm.uiState.value.handling?.flowId)
            assertEquals(ProtectedTrafficAction.BLOCK, vm.uiState.value.handling?.decision?.action)
            assertNull(vm.uiState.value.feedback)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `previous row callbacks cannot mutate the newly selected destination`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = ProtectionFixture(); fixture.repository.setDefaultAction(ProtectedTrafficAction.TUNNEL)
        val vm = fixture.viewModel(); val store = ViewModelStore().apply { put("vm", vm) }
        try {
            val source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other
            vm.selectCapturedFlow(fixture.captured, source); runCurrent()
            val next = fixture.captured.copy(id = OpaqueFlowId("next"), serverName = "different.test")
            fixture.record = next; vm.selectCapturedFlow(next, source); runCurrent()
            vm.applyDestinationAction(ProtectedTrafficAction.BLOCK, fixture.captured.id)
            vm.useDefaultHandling(fixture.captured.id); runCurrent()
            assertEquals(0, fixture.writes)
            assertEquals(next.id, vm.uiState.value.flowId)
            assertEquals(ProtectedTrafficAction.TUNNEL, vm.uiState.value.handling?.decision?.action)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `canonical removal clears the immediate policy and same ID metadata changes restart observation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = ProtectionFixture(); fixture.repository.setDefaultAction(ProtectedTrafficAction.TUNNEL)
        val vm = fixture.viewModel(); val store = ViewModelStore().apply { put("vm", vm) }
        try {
            val source = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other
            vm.selectCapturedFlow(fixture.captured, source); runCurrent()
            val changed = fixture.captured.copy(serverName = "changed.test")
            fixture.record = null; vm.selectCapturedFlow(changed, source)
            assertNotNull(vm.uiState.value.handling)
            runCurrent(); assertNull(vm.uiState.value.handling)
            assertIs<TrafficProtectionFeedback.Failed>(vm.uiState.value.feedback)
            fixture.repository.setDefaultAction(ProtectedTrafficAction.BLOCK); runCurrent()
            assertNull(vm.uiState.value.handling)
            assertEquals(2, fixture.reads)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `automatic fallback can be overridden blocked and reset across selection changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = ProtectionFixture()
        val session = com.devuloopers.knet.traffic.id.CaptureSessionId("current")
        fixture.context = fixture.context!!.copy(sessionId = session)
        val key = com.devuloopers.knet.application.contract.protectedtraffic.automaticTlsFallbackKey(session,
            com.devuloopers.knet.traffic.model.tlsFallbackClientScope(fixture.context!!.ingress, fixture.context!!.downstreamHost),
            fixture.captured.serverName!!, 443, null)!!
        fixture.fallbackState.value = com.devuloopers.knet.application.contract.protectedtraffic.AutomaticTlsFallbackState(session, 1, setOf(key))
        val vm = fixture.viewModel(); val store = ViewModelStore().apply { put("vm", vm) }
        try {
            vm.selectFlow(fixture.captured.id); runCurrent()
            assertEquals(ProtectedTrafficDecisionEvidence.AutomaticFallback, vm.uiState.value.handling!!.decision.evidence)
            vm.applyDestinationAction(ProtectedTrafficAction.INSPECT); runCurrent()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.INSPECT).selected)
            assertTrue(fixture.fallbackState.value.keys.isEmpty())
            vm.selectFlow(OpaqueFlowId("other")); runCurrent(); vm.selectFlow(fixture.captured.id); runCurrent()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.INSPECT).selected)
            vm.applyDestinationAction(ProtectedTrafficAction.BLOCK); runCurrent()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.BLOCK).selected)
            vm.useDefaultHandling(); runCurrent()
            assertIs<TrafficProtectionFeedback.DefaultRestored>(vm.uiState.value.feedback)
            assertNull(vm.uiState.value.handling!!.destinationRule)
            assertEquals(ProtectedTrafficDecisionEvidence.GlobalDefault, vm.uiState.value.handling!!.decision.evidence)
            assertTrue(fixture.repository.configuration.value.rules.isEmpty())
            assertEquals(OpaqueFlowPolicyAction.INSPECT, fixture.captured.policyAction)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `tunnel and inspect update one rule in place while captured failure remains unchanged`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply { put("vm",vm) }
        try {
            vm.selectFlow(fixture.captured.id);runCurrent()
            vm.applyFlowAction("flow",ProtectedTrafficAction.TUNNEL,ProtectedTrafficQuickRuleScope.DESTINATION);runCurrent()
            val id=fixture.repository.configuration.value.rules.single().id
            assertEquals(ProtectedTrafficAction.TUNNEL,vm.uiState.value.handling!!.decision.action)
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            assertIs<TrafficProtectionFeedback.Saved>(vm.uiState.value.feedback)
            vm.applyFlowAction("flow",ProtectedTrafficAction.INSPECT,ProtectedTrafficQuickRuleScope.DESTINATION);runCurrent()
            assertEquals(id,fixture.repository.configuration.value.rules.single().id)
            assertEquals(ProtectedTrafficAction.INSPECT,vm.uiState.value.handling!!.decision.action)
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.INSPECT).selected)
            assertEquals(OpaqueFlowPolicyAction.INSPECT,fixture.captured.policyAction)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `failed save reports inline error and permits a later successful action`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            vm.selectFlow(fixture.captured.id);runCurrent()
            fixture.beforeSave={error("Storage unavailable")}
            vm.applyFlowAction("flow",ProtectedTrafficAction.TUNNEL,ProtectedTrafficQuickRuleScope.DESTINATION);runCurrent()
            assertEquals("Storage unavailable",assertIs<TrafficProtectionFeedback.Failed>(vm.uiState.value.feedback).message)
            assertFalse(vm.uiState.value.isApplying);assertTrue(fixture.repository.configuration.value.rules.isEmpty())
            fixture.beforeSave={}
            vm.applyFlowAction("flow",ProtectedTrafficAction.TUNNEL,ProtectedTrafficQuickRuleScope.DESTINATION);runCurrent()
            assertIs<TrafficProtectionFeedback.Saved>(vm.uiState.value.feedback)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `a pending save cannot double submit or report success on a different selected row`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            vm.selectFlow(fixture.captured.id);runCurrent()
            val release=CompletableDeferred<Unit>();fixture.beforeSave={release.await()}
            vm.applyFlowAction("flow",ProtectedTrafficAction.TUNNEL,ProtectedTrafficQuickRuleScope.DESTINATION);runCurrent()
            vm.applyFlowAction("flow",ProtectedTrafficAction.TUNNEL,ProtectedTrafficQuickRuleScope.DESTINATION)
            vm.selectFlow(OpaqueFlowId("other"));runCurrent()
            assertTrue(vm.uiState.value.isApplying)
            release.complete(Unit);runCurrent()
            assertEquals(1,fixture.writes);assertNull(vm.uiState.value.feedback)
            assertEquals("other",vm.uiState.value.flowId!!.value);assertFalse(vm.uiState.value.isApplying)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `changing selection cancels an unfinished metadata read`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            fixture.beforeRead={id -> if(id==fixture.captured.id) awaitCancellation()}
            vm.selectFlow(fixture.captured.id);runCurrent();assertTrue(vm.uiState.value.isLoading)
            vm.selectFlow(OpaqueFlowId("other"));runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals("other",vm.uiState.value.handling!!.flowId.value)
            assertNull(vm.uiState.value.feedback)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `both saved choices survive row changes and a fresh ViewModel without another save`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            listOf(ProtectedTrafficAction.TUNNEL,ProtectedTrafficAction.INSPECT).forEach { action ->
                val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
                try {
                    vm.selectFlow(fixture.captured.id);runCurrent();vm.applyDestinationAction(action);runCurrent()
                    val rule=fixture.repository.configuration.value.rules.single()
                    assertTrue(vm.uiState.value.destinationAction(action).selected)
                    fixture.record=fixture.captured.copy(serverName="other.example.com")
                    vm.selectFlow(OpaqueFlowId("other"));runCurrent()
                    assertFalse(vm.uiState.value.destinationAction(action).selected)
                    fixture.record=fixture.captured
                    vm.selectFlow(fixture.captured.id);runCurrent()
                    assertTrue(vm.uiState.value.destinationAction(action).selected)
                    assertNull(vm.uiState.value.feedback,"Persisted selection cannot depend on a transient success message")
                    vm.applyDestinationAction(action);runCurrent()
                    assertEquals(1,fixture.writes,"An active exact choice must not be submitted twice")
                    vm.selectFlow(OpaqueFlowId("matching-history"));runCurrent()
                    assertTrue(vm.uiState.value.destinationAction(action).selected)
                    assertEquals(rule.id,vm.uiState.value.handling!!.destinationRule!!.id)
                    store.clear()
                    val recreated=fixture.viewModel();val secondStore=ViewModelStore().apply {put("vm",recreated)}
                    try { recreated.selectFlow(fixture.captured.id);runCurrent()
                        assertTrue(recreated.uiState.value.destinationAction(action).selected)
                        assertEquals(1,fixture.writes)
                    } finally {secondStore.clear()}
                } finally {store.clear()}
            }
        } finally {Dispatchers.resetMain()}
    }

    @Test fun `external rule replacement disable deletion and Forget update selected policy`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            vm.selectFlow(fixture.captured.id);runCurrent();vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            val rule=fixture.repository.configuration.value.rules.single()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            fixture.repository.upsertRule(rule.copy(action=ProtectedTrafficAction.INSPECT));runCurrent()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.INSPECT).selected)
            fixture.repository.upsertRule(rule.copy(enabled=false));runCurrent()
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).enabled)
            fixture.repository.upsertRule(rule);runCurrent()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            fixture.repository.deleteRule(rule.id);runCurrent()
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `failed alternative preserves the active saved selection`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            vm.selectFlow(fixture.captured.id);runCurrent();vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            fixture.beforeSave={error("Unable to save")}
            vm.applyDestinationAction(ProtectedTrafficAction.INSPECT);runCurrent()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.INSPECT).selected)
            assertIs<TrafficProtectionFeedback.Failed>(vm.uiState.value.feedback)
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.INSPECT).enabled)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `unready inspector cannot submit and cancelling a pending save cannot create selection`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            fixture.beforeRead={awaitCancellation()}
            vm.selectFlow(fixture.captured.id);runCurrent();vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            assertEquals(0,fixture.writes)
            fixture.beforeRead={};vm.selectFlow(null);vm.selectFlow(fixture.captured.id);runCurrent()
            fixture.repository.configuration.value=fixture.repository.configuration.value.copy(configurationReady=false);runCurrent()
            vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent();assertEquals(0,fixture.writes)
            fixture.repository.configuration.value=fixture.repository.configuration.value.copy(configurationReady=true);runCurrent()
            fixture.beforeSave={awaitCancellation()}
            vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            assertEquals(TrafficProtectionActionStatus.SAVING,vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).status)
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            store.clear();runCurrent()
            assertTrue(fixture.repository.configuration.value.rules.isEmpty())
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `returning after a delayed save reconstructs committed selection without stale feedback`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            vm.selectFlow(fixture.captured.id);runCurrent()
            val release=CompletableDeferred<Unit>();fixture.beforeSave={release.await()}
            vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            vm.selectFlow(OpaqueFlowId("other"));runCurrent()
            release.complete(Unit);runCurrent()
            assertNull(vm.uiState.value.feedback)
            vm.selectFlow(fixture.captured.id);runCurrent()
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            assertNull(vm.uiState.value.feedback);assertEquals(1,fixture.writes)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `a committed write stays saving until authoritative policy publication arrives`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture()
        var pending:ProtectedTrafficRule?=null
        var writes=0
        val repository=object:com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository by fixture.repository {
            override fun forWorkspace(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace) = this
            override suspend fun saveDefinition(rule: ProtectedTrafficRule, context: com.devuloopers.knet.domain.policy.RuleEditContext): com.devuloopers.knet.domain.policy.RuleSaveResult { upsertRule(rule); return com.devuloopers.knet.domain.policy.RuleSaveResult() }
            override suspend fun upsertRule(rule:ProtectedTrafficRule) {writes++;pending=rule}
        }
        val vm=TrafficProtectionViewModel(ObserveProtectedTrafficFlowHandlingUseCase(fixture.query,repository),
            SetProtectedTrafficFlowActionUseCase(fixture.query,repository), ResetProtectedTrafficFlowActionUseCase(fixture.query,repository,fixture.fallback))
        val store=ViewModelStore().apply {put("vm",vm)}
        try {
            vm.selectFlow(fixture.captured.id);runCurrent()
            vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            assertTrue(vm.uiState.value.isApplying)
            assertEquals(TrafficProtectionActionStatus.SAVING,vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).status)
            vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent();assertEquals(1,writes)
            fixture.repository.configuration.value=fixture.repository.configuration.value.copy(rules=listOf(assertNotNull(pending)))
            runCurrent()
            assertFalse(vm.uiState.value.isApplying)
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            assertIs<TrafficProtectionFeedback.Saved>(vm.uiState.value.feedback)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `missing publication reports an error without inventing a selected rule`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture()
        val repository=object:com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository by fixture.repository {
            override fun forWorkspace(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace) = this
            override suspend fun saveDefinition(rule: ProtectedTrafficRule, context: com.devuloopers.knet.domain.policy.RuleEditContext): com.devuloopers.knet.domain.policy.RuleSaveResult { upsertRule(rule); return com.devuloopers.knet.domain.policy.RuleSaveResult() }
            override suspend fun upsertRule(rule:ProtectedTrafficRule)=Unit
        }
        val vm=TrafficProtectionViewModel(ObserveProtectedTrafficFlowHandlingUseCase(fixture.query,repository),
            SetProtectedTrafficFlowActionUseCase(fixture.query,repository), ResetProtectedTrafficFlowActionUseCase(fixture.query,repository,fixture.fallback))
        val store=ViewModelStore().apply {put("vm",vm)}
        try {
            vm.selectFlow(fixture.captured.id);runCurrent();vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            advanceTimeBy(5001);runCurrent()
            assertFalse(vm.uiState.value.isApplying)
            assertIs<TrafficProtectionFeedback.Failed>(vm.uiState.value.feedback)
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            assertTrue(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).enabled)
        } finally {store.clear();Dispatchers.resetMain()}
    }

    @Test fun `cleared history leaves no selected or actionable stale policy`()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture=ProtectionFixture();val vm=fixture.viewModel();val store=ViewModelStore().apply {put("vm",vm)}
        try {
            fixture.record=null;vm.selectFlow(fixture.captured.id);runCurrent()
            assertFalse(vm.uiState.value.isLoading);assertNull(vm.uiState.value.handling)
            assertIs<TrafficProtectionFeedback.Failed>(vm.uiState.value.feedback)
            vm.applyDestinationAction(ProtectedTrafficAction.TUNNEL);runCurrent()
            assertEquals(0,fixture.writes)
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
            assertFalse(vm.uiState.value.destinationAction(ProtectedTrafficAction.TUNNEL).enabled)
        } finally {store.clear();Dispatchers.resetMain()}
    }

}
