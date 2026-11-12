package com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedTrafficRuleDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Management operations preserve scope/provenance and remain safe under pending, failed and external mutations. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtectedTrafficManagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `editing imported wildcard selectors creates a user rule retaining identity transport port family and disabled state`() = runTest(dispatcher) {
        val original = rule("imported").copy(enabled = false, origin = ProtectedTrafficRuleOrigin.MIGRATED_PRESET,
            port = null, transport = null, ipFamily = ProtectedIpFamily.IPV6)
        val repository = Repository(listOf(original))
        val manager = manager(repository); advanceUntilIdle()
        manager.beginEditRule(original)
        assertNull(manager.state.value.ruleDraft!!.transport)
        assertEquals("", manager.state.value.ruleDraft!!.port)
        manager.updateRuleDraft(manager.state.value.ruleDraft!!.copy(destination = "*.new.example.com"))
        manager.saveRuleDraft(); advanceUntilIdle()
        assertEquals(original.copy(destination = ProtectedDestinationSelector.parse("*.new.example.com"), origin = ProtectedTrafficRuleOrigin.USER), repository.configuration.value.rules.single())
    }

    @Test fun `unchanged imported saves and enable toggles retain imported provenance`() = runTest(dispatcher) {
        val original = rule("imported").copy(origin = ProtectedTrafficRuleOrigin.MIGRATED_PRESET)
        val repository = Repository(listOf(original)); val manager = manager(repository); advanceUntilIdle()
        manager.beginEditRule(original); manager.saveRuleDraft(); advanceUntilIdle()
        assertEquals(original, repository.configuration.value.rules.single())
        manager.setRuleEnabled(original, false); advanceUntilIdle()
        assertEquals(original.copy(enabled = false), repository.configuration.value.rules.single())
        manager.beginEditRule(original)
        manager.updateRuleDraft(manager.state.value.ruleDraft!!.copy(action = ProtectedTrafficAction.BLOCK))
        manager.saveRuleDraft(); advanceUntilIdle()
        assertEquals(original.copy(enabled = false, action = ProtectedTrafficAction.BLOCK, origin = ProtectedTrafficRuleOrigin.USER),
            repository.configuration.value.rules.single())
    }

    @Test fun `search observes external Traffic updates without changing policy order or open draft`() = runTest(dispatcher) {
        val repository = Repository()
        val manager = manager(repository); advanceUntilIdle()
        manager.updateSearchQuery("video 443"); manager.beginAddRule()
        val draft = manager.state.value.ruleDraft
        val external = rule("from-traffic").copy(destination = ProtectedDestinationSelector.parse("video.example.com"))
        repository.upsertRule(external); advanceUntilIdle()
        assertEquals(listOf(external), manager.state.value.filteredRules)
        assertEquals(draft, manager.state.value.ruleDraft)
        assertEquals("video 443", manager.state.value.searchQuery)
        assertEquals(listOf(external), repository.configuration.value.rules)
    }

    @Test fun `pending save blocks duplicate submission dismissal and editor replacement`() = runTest(dispatcher) {
        val repository = Repository(); val gate = CompletableDeferred<Unit>(); repository.gate = gate
        val manager = manager(repository); advanceUntilIdle()
        manager.beginAddRule(); manager.updateRuleDraft(ProtectedTrafficRuleDraft(destination = "one.example.com"))
        manager.saveRuleDraft(); manager.saveRuleDraft(); manager.dismissRuleDraft(); manager.beginAddRule()
        runCurrent()
        assertTrue(manager.state.value.isSaving)
        assertEquals("one.example.com", manager.state.value.ruleDraft!!.destination)
        assertEquals(1, repository.writes)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, repository.configuration.value.rules.size)
        assertNull(manager.state.value.ruleDraft); assertFalse(manager.state.value.isSaving)
    }

    @Test fun `invalid ports and backend errors retain a correctable draft and release saving state`() = runTest(dispatcher) {
        val repository = Repository(); val manager = manager(repository); advanceUntilIdle()
        for (port in listOf("0", "65536", "44bad3", "999999999999999999999")) {
            manager.beginAddRule(); manager.updateRuleDraft(ProtectedTrafficRuleDraft(destination = "valid.example.com", port = port))
            manager.saveRuleDraft(); advanceUntilIdle()
            assertTrue(manager.state.value.draftError.orEmpty().isNotBlank())
            assertEquals(port, manager.state.value.ruleDraft!!.port)
            assertFalse(manager.state.value.isSaving)
        }
        assertEquals(0, repository.writes)
        repository.failSave = true
        manager.updateRuleDraft(manager.state.value.ruleDraft!!.copy(port = "443"))
        manager.saveRuleDraft(); advanceUntilIdle()
        assertEquals("fixture persistence failure", manager.state.value.draftError)
        assertTrue(repository.configuration.value.rules.isEmpty())
        repository.failSave = false
        manager.updateRuleDraft(manager.state.value.ruleDraft!!.copy(port = ""))
        manager.saveRuleDraft(); advanceUntilIdle()
        assertNull(repository.configuration.value.rules.single().port)
    }

    @Test fun `deleted editor target cannot be silently recreated`() = runTest(dispatcher) {
        val original = rule("deleted"); val repository = Repository(listOf(original))
        val manager = manager(repository); advanceUntilIdle()
        manager.beginEditRule(original)
        repository.deleteRule(original.id); advanceUntilIdle()
        manager.saveRuleDraft(); advanceUntilIdle()
        assertTrue(repository.configuration.value.rules.isEmpty())
        assertTrue(manager.state.value.draftError.orEmpty().contains("removed"))
        manager.dismissRuleDraft(); manager.beginEditRule(original)
        assertNull(manager.state.value.ruleDraft)
        assertEquals(0, repository.writes)
    }

    @Test fun `rule writes serialize by identity and use the latest authoritative row`() = runTest(dispatcher) {
        val old = rule("same"); val latest = old.copy(destination = ProtectedDestinationSelector.parse("new.example.com"))
        val repository = Repository(listOf(latest)); val manager = manager(repository); advanceUntilIdle()
        manager.beginEditRule(old)
        assertEquals("new.example.com", manager.state.value.ruleDraft!!.destination)
        manager.dismissRuleDraft()
        val gate = CompletableDeferred<Unit>(); repository.gate = gate
        manager.setRuleEnabled(old, false); manager.deleteRule(old.id); runCurrent()
        assertEquals(setOf(old.id), manager.state.value.pendingRuleIds)
        assertEquals(0, repository.deletes)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(latest.copy(enabled = false), repository.configuration.value.rules.single())
        assertTrue(manager.state.value.pendingRuleIds.isEmpty())
    }

    @Test fun `default writes serialize and recover after a persistence failure`() = runTest(dispatcher) {
        val repository = Repository(); val gate = CompletableDeferred<Unit>()
        repository.defaultGate = gate; repository.failDefault = true
        val manager = manager(repository); advanceUntilIdle()
        manager.selectDefaultHandling(ProtectedTrafficDefaultHandling.TUNNEL)
        manager.selectDefaultHandling(ProtectedTrafficDefaultHandling.BLOCK); runCurrent()
        assertTrue(manager.state.value.isSettingDefault)
        assertEquals(1, repository.defaultWrites)
        assertEquals(ProtectedTrafficAction.INSPECT, manager.state.value.configuration.defaultAction)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(manager.state.value.isSettingDefault)
        assertNotNull(manager.state.value.errorMessage)
        manager.clearError(); repository.failDefault = false; repository.defaultGate = null
        manager.selectDefaultHandling(ProtectedTrafficDefaultHandling.TUNNEL); advanceUntilIdle()
        assertEquals(ProtectedTrafficAction.TUNNEL, manager.state.value.configuration.defaultAction)
        assertNull(manager.state.value.errorMessage)
    }

    @Test fun `failed enable and delete operations release pending state and retain saved rules`() = runTest(dispatcher) {
        val original = rule("retained"); val repository = Repository(listOf(original))
        val manager = manager(repository); advanceUntilIdle()
        repository.failSave = true; manager.setRuleEnabled(original, false); advanceUntilIdle()
        assertTrue(manager.state.value.pendingRuleIds.isEmpty())
        assertNotNull(manager.state.value.errorMessage)
        assertEquals(original, repository.configuration.value.rules.single())
        manager.clearError(); repository.failDelete = true
        manager.deleteRule(original.id); advanceUntilIdle()
        assertTrue(manager.state.value.pendingRuleIds.isEmpty())
        assertNotNull(manager.state.value.errorMessage)
        assertEquals(original, repository.configuration.value.rules.single())
        manager.clearError(); repository.failDelete = false
        manager.deleteRule(original.id); advanceUntilIdle()
        assertTrue(repository.configuration.value.rules.isEmpty())
    }

    @Test fun `owner cancellation releases pending state without displaying a save error`() = runTest(dispatcher) {
        val repository = Repository(); repository.gate = CompletableDeferred()
        val manager = manager(repository); advanceUntilIdle()
        manager.beginAddRule(); manager.saveRuleDraft(); runCurrent()
        store.clear(); advanceUntilIdle()
        assertFalse(manager.state.value.isSaving)
        assertNull(manager.state.value.draftError)
        assertTrue(repository.configuration.value.rules.isEmpty())
    }

    @Test fun `unready policy and unsupported bypass cannot mutate the default or create a draft`() = runTest(dispatcher) {
        val repository = Repository(); repository.configuration.value = ProtectedTrafficConfiguration(configurationReady = false)
        val manager = manager(repository); advanceUntilIdle()
        manager.beginAddRule(); manager.selectDefaultHandling(ProtectedTrafficDefaultHandling.TUNNEL); advanceUntilIdle()
        assertNull(manager.state.value.ruleDraft)
        assertEquals(ProtectedTrafficAction.INSPECT, repository.configuration.value.defaultAction)
        repository.configuration.value = ProtectedTrafficConfiguration(); advanceUntilIdle()
        manager.selectDefaultHandling(ProtectedTrafficDefaultHandling.INSPECT_ONLY); advanceUntilIdle()
        assertEquals(ProtectedTrafficAction.INSPECT, repository.configuration.value.defaultAction)
    }

    private fun rule(id: String) = ProtectedTrafficRule(ProtectedTrafficRuleId(id), action = ProtectedTrafficAction.TUNNEL,
        destination = ProtectedDestinationSelector.parse("*.old.example.com"), port = 443, transport = ProtectedTransportProtocol.TCP)
    private fun manager(repository: ProtectedTrafficRepository) = ProtectedTrafficViewModel(
        ObserveProtectedTrafficConfigurationUseCase(repository), SetProtectedTrafficDefaultActionUseCase(repository),
        UpsertProtectedTrafficRuleUseCase(repository), DeleteProtectedTrafficRuleUseCase(repository),
    ).also { store.put("manager", it) }

    private class Repository(rules: List<ProtectedTrafficRule> = emptyList()) : ProtectedTrafficRepository {
        override val configuration = MutableStateFlow(ProtectedTrafficConfiguration(rules = rules))
        var gate: CompletableDeferred<Unit>? = null
        var writes = 0
        var deletes = 0
        var failSave = false
        var failDelete = false
        var failDefault = false
        var defaultWrites = 0
        var defaultGate: CompletableDeferred<Unit>? = null
        override suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean) {
            defaultWrites++; defaultGate?.await()
            if (failDefault) error("fixture persistence failure")
            configuration.value = configuration.value.copy(defaultAction = action, automaticTlsFallbackEnabled = automaticFallbackEnabled)
        }
        override suspend fun upsertRule(rule: ProtectedTrafficRule) {
            writes++; gate?.await()
            if (failSave) error("fixture persistence failure")
            configuration.value = configuration.value.copy(rules = configuration.value.rules.filterNot { it.id == rule.id } + rule)
        }
        override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) {
            deletes++
            if (failDelete) error("fixture persistence failure")
            configuration.value = configuration.value.copy(rules = configuration.value.rules.filterNot { it.id == ruleId })
        }
    }
}
