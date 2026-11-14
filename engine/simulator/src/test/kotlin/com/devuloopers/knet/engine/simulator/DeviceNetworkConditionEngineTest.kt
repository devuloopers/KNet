package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Concurrent-device budgets, configuration changes, queue fairness and subscriber-owned telemetry. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceNetworkConditionEngineTest {
    private val a: PolicyWorkspace = PolicyWorkspace.Device(RegisteredDeviceId("a"))
    private val b: PolicyWorkspace = PolicyWorkspace.Device(RegisteredDeviceId("b"))
    private val profile = NetworkConditionProfile(NetworkConditionProfileId("both-100k"), "Both 100 kbps",
        download = NetworkDirectionCondition(100_000), upload = NetworkDirectionCondition(100_000))
    private fun configured() = NetworkConditionConfiguration(enabled = true, customProfiles = listOf(profile),
        rules = listOf(NetworkConditionRule(NetworkConditionRuleId("shared"), NetworkConditionTarget.parse("stream.test"), profile.id)))

    @Test fun `idle workspace still receives real one second samples without allocating a flow`() = runTest {
        var now=0L
        val engine=NetworkConditionEngine({ NetworkConditionConfiguration() },nanoTime={ now })
        engine.bindWorkspaces(MutableStateFlow(mapOf(a to configured())),backgroundScope)
        val samples=mutableListOf<com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot>()
        backgroundScope.launch { engine.observeWorkspace(a).collect { samples+=it } }
        runCurrent()
        now=1_000_000_000L; advanceTimeBy(1_000); runCurrent()
        assertEquals(now,samples.last().sampledAtNanos)
        assertEquals(0,samples.last().activeFlows); assertEquals(0L,samples.last().downloadedBytes)
    }

    @Test fun `policy committed between initial read and collection cannot be dropped`() = runTest {
        val committed=MutableStateFlow<Map<PolicyWorkspace,NetworkConditionConfiguration>>(emptyMap())
        val source=object:kotlinx.coroutines.flow.StateFlow<Map<PolicyWorkspace,NetworkConditionConfiguration>> by committed {
            override val value:Map<PolicyWorkspace,NetworkConditionConfiguration> get() {
                val before=committed.value
                committed.value=mapOf(a to configured())
                return before
            }
        }
        val engine=NetworkConditionEngine({ NetworkConditionConfiguration() })
        engine.bindWorkspaces(source,backgroundScope)
        assertTrue(engine.configurationSnapshot(a).enabled)
    }

    @Test fun `same shared rule is aggregate within each device and independent in both directions`() = runTest {
        val policies = MutableStateFlow(mapOf(a to configured(), b to configured()))
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() }, nanoTime = { 0L })
        engine.bindWorkspaces(policies, backgroundScope)
        for (direction in NetworkConditionDirection.entries) {
            for (workspace in listOf(a, b)) {
                repeat(60) { index ->
                    val plan = assertIs<NetworkConditionPlan.Delay>(engine.plan("stream.test", 443, direction, 1_250, index.toLong(), workspace))
                    assertEquals((index + 1L) * 100_000_000, plan.nanoseconds)
                }
            }
        }
        assertIs<NetworkConditionPlan.PassThrough>(engine.plan("stream.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 1, PolicyWorkspace.Other))
        assertIs<NetworkConditionPlan.PassThrough>(engine.plan("unmatched.test", 443, NetworkConditionDirection.DOWNLOAD, 1_250, 1, a))
    }

    @Test fun `changing A cannot reset B bandwidth reservation or invoke B listeners`() = runTest {
        val policies = MutableStateFlow(mapOf(a to configured(), b to configured()))
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() }, nanoTime = { 0L })
        engine.bindWorkspaces(policies, backgroundScope)
        assertEquals(1_000_000_000L, assertIs<NetworkConditionPlan.Delay>(engine.plan("stream.test",443, NetworkConditionDirection.DOWNLOAD,12_500,1,b)).nanoseconds)
        policies.value = policies.value + (a to NetworkConditionConfiguration())
        runCurrent()
        assertEquals(2_000_000_000L, assertIs<NetworkConditionPlan.Delay>(engine.plan("stream.test",443, NetworkConditionDirection.DOWNLOAD,12_500,2,b)).nanoseconds)
        assertIs<NetworkConditionPlan.PassThrough>(engine.plan("stream.test",443, NetworkConditionDirection.DOWNLOAD,12_500,2,a))
    }

    @Test fun `one device cannot consume sibling queue share and failed global reservation rolls back`() = runTest {
        val policies = MutableStateFlow(mapOf(a to configured(), b to configured()))
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() }, maximumQueuedBytes = 100)
        engine.bindWorkspaces(policies, backgroundScope)
        assertFalse(engine.tryQueue(51,a))
        assertTrue(engine.tryQueue(50,a)); assertFalse(engine.tryQueue(1,a))
        assertTrue(engine.tryQueue(50,b)); assertFalse(engine.tryQueue(1,b))
        engine.releaseQueued(50,a); assertTrue(engine.tryQueue(50,a))
        engine.releaseQueued(50,a); engine.releaseQueued(50,b); engine.refreshTelemetry()
        assertEquals(0L, engine.snapshot.value.queuedBytes)
    }

    @Test fun `one telemetry clock publishes only owning device bytes without config edit ticks`() = runTest {
        var now = 0L
        val policies = MutableStateFlow(mapOf(a to configured(), b to configured()))
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() }, nanoTime = { now })
        engine.bindWorkspaces(policies, backgroundScope)
        val samplesA = mutableListOf<com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot>()
        val samplesB = mutableListOf<com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot>()
        backgroundScope.launch { engine.observeWorkspace(a).collect { samplesA += it } }
        backgroundScope.launch { engine.observeWorkspace(b).collect { samplesB += it } }
        runCurrent()
        engine.recordForwarded(NetworkConditionDirection.UPLOAD,123,a)
        engine.recordForwarded(NetworkConditionDirection.DOWNLOAD,456,b)
        repeat(100) { policies.value = policies.value + (a to configured().copy(enabled = it % 2 == 0)); runCurrent() }
        val before = samplesA.size
        assertEquals(0L, samplesA.last().uploadedBytes)
        now = 1_000_000_000; advanceTimeBy(1_000); runCurrent()
        assertEquals(before + 1, samplesA.size)
        assertEquals(123L,samplesA.last().uploadedBytes); assertEquals(0L,samplesA.last().downloadedBytes)
        assertEquals(456L,samplesB.last().downloadedBytes); assertEquals(0L,samplesB.last().uploadedBytes)
    }

    @Test fun `ten workspaces keep flow and byte accounting separate with idempotent close`() = runTest {
        val workspaces = (1..10).map { PolicyWorkspace.Device(RegisteredDeviceId("device-$it")) }
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration() })
        engine.bindWorkspaces(MutableStateFlow(workspaces.associateWith { configured() }), backgroundScope)
        val handles = workspaces.map(engine::openFlow)
        workspaces.forEachIndexed { index, workspace -> engine.recordForwarded(NetworkConditionDirection.DOWNLOAD,index+1,workspace) }
        engine.refreshTelemetry(); assertEquals(10,engine.snapshot.value.activeFlows); assertEquals(55L,engine.snapshot.value.downloadedBytes)
        handles.forEach { it.close(); it.close() }
        engine.refreshTelemetry(); assertEquals(0,engine.snapshot.value.activeFlows)
    }
}
