package com.devuloopers.knet.ui.desktop.app.workspace

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.device.*
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.usecase.device.*
import com.devuloopers.knet.identity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Roster updates cannot steal user selection or start a sampler for every retained/offline tab. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceWorkspaceViewModelTest {
    @Test fun `restoration presence and switching keep one sampler with independent zero baselines`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val a = RegisteredDevice(RegisteredDeviceId("a"), "Pixel", DeviceRegistrationKind.PAIRED_COMPANION, 1, 1)
            val b = a.copy(id = RegisteredDeviceId("b"), registeredAtEpochMillis = 2, lastSeenAtEpochMillis = 2)
            val roster = MutableStateFlow(listOf(b, a))
            val presence = object : DevicePresenceReader { override val presence = MutableStateFlow<Map<RegisteredDeviceId, DevicePresence>>(emptyMap()) }
            val registry = object : RegisteredDeviceStore {
                override suspend fun putRegisteredDevice(device: RegisteredDevice) = Unit
                override suspend fun getRegisteredDevice(id: RegisteredDeviceId) = roster.value.find { it.id == id }
                override suspend fun markRegisteredDeviceSeen(id: RegisteredDeviceId, seenAtEpochMillis: Long) = false
                override suspend fun revokeRegisteredDevice(id: RegisteredDeviceId, revokedAtEpochMillis: Long) = false
                override fun observeRegisteredDevices(): Flow<List<RegisteredDevice>> = roster
            }
            val sourceA = TrafficSource.Device(a.id); val sourceB = TrafficSource.Device(b.id)
            val prefs = object : DeviceWorkspacePreferences {
                override val selectedSource = MutableStateFlow<TrafficSource>(sourceA)
                override suspend fun select(source: TrafficSource) { selectedSource.value = source }
                override val openDevices = MutableStateFlow<Set<RegisteredDeviceId>>(emptySet())
                override suspend fun setOpen(id: RegisteredDeviceId, open: Boolean) { openDevices.value = if (open) openDevices.value + id else openDevices.value - id }
            }
            var reads = 0
            val telemetry = SourceTrafficTelemetry { reads++
                SourceTrafficSnapshot(testScheduler.currentTime * 1_000_000, testScheduler.currentTime * 125, testScheduler.currentTime * 250, 1) }
            val viewModel = DeviceWorkspaceViewModel(ObserveDeviceWorkspacesUseCase(registry, presence, object : DeviceTrafficHistory {
                override fun observeDeviceIdsWithTraffic() = flowOf(setOf(a.id, b.id))
            }, prefs), ObserveDeviceWorkspaceSelectionUseCase(prefs),
                SelectDeviceWorkspaceUseCase(prefs), ObserveSourceTrafficUseCase(telemetry), SetDeviceWorkspaceOpenUseCase(prefs))
            owner.put("workspace", viewModel)
            runCurrent()
            assertEquals(sourceA, viewModel.state.value.source)
            assertEquals(listOf(a.id, b.id), viewModel.state.value.devices.map { it.device.id })
            val navigationUpdates = mutableListOf<DeviceWorkspaceState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.navigationState.collect { navigationUpdates += it } }
            val beforeSamples = navigationUpdates.size
            viewModel.setMonitoring(true); viewModel.setMonitoring(true); runCurrent()
            assertEquals(1, reads)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(2, reads)
            assertEquals(2_000_000L, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
            advanceTimeBy(60_000); runCurrent()
            assertEquals(beforeSamples, navigationUpdates.size, "Throughput must not invalidate shell navigation")
            viewModel.select(sourceB); runCurrent()
            assertEquals(beforeSamples + 1, navigationUpdates.size)
            assertEquals(0, viewModel.state.value.throughput.currentDownloadBitsPerSecond)
            presence.presence.value = mapOf(a.id to DevicePresence(DevicePresenceStatus.CONNECTED, "run", 0))
            runCurrent()
            assertEquals(sourceB, viewModel.state.value.source)
            assertEquals(sourceB, prefs.selectedSource.value)
            viewModel.setMonitoring(false)
            val stoppedReads = reads
            advanceTimeBy(60_000); runCurrent()
            assertEquals(stoppedReads, reads)
            viewModel.select(TrafficSource.Device(RegisteredDeviceId("unknown")))
            assertEquals(sourceB, viewModel.state.value.source)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
    @Test fun `offline Close confirms exact target while Clear retains the tab`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.viewModel.select(fixture.sourceB)
            fixture.viewModel.requestClose(fixture.a.id)
            assertEquals(fixture.sourceB, fixture.viewModel.state.value.source)
            assertEquals(fixture.sourceA, fixture.viewModel.state.value.clearConfirmation?.source)
            fixture.viewModel.requestClose(fixture.b.id)
            assertEquals(fixture.sourceA, fixture.viewModel.state.value.clearConfirmation?.source)
            fixture.viewModel.dismissClear()
            assertNull(fixture.viewModel.confirmClear())
            assertEquals(setOf(fixture.a.id, fixture.b.id), fixture.retained.value)
            assertEquals(fixture.sourceA, fixture.viewModel.requestClear(fixture.sourceA))
            assertNull(fixture.viewModel.state.value.clearConfirmation)
            fixture.viewModel.retainForClear(fixture.sourceA)
            fixture.retained.value -= fixture.a.id
            runCurrent()
            assertTrue(fixture.viewModel.state.value.devices.any { it.device.id == fixture.a.id })
            fixture.viewModel.requestClose(fixture.a.id)
            fixture.viewModel.select(TrafficSource.Other)
            assertEquals(fixture.sourceA, fixture.viewModel.confirmClear())
            fixture.viewModel.finishClose(fixture.sourceA)
            runCurrent()
            assertTrue(fixture.viewModel.state.value.devices.none { it.device.id == fixture.a.id })
            assertNull(fixture.viewModel.confirmClear(), "Confirmation must be consumed once")
            assertEquals(TrafficSource.Other, fixture.viewModel.state.value.source)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `online and reconnecting Clear keep empty tabs and cannot be closed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.setPresence(fixture.a.id, DevicePresenceStatus.CONNECTED)
            fixture.setPresence(fixture.b.id, DevicePresenceStatus.RECONNECTING)
            fixture.retained.value = emptySet()
            runCurrent()
            assertEquals(2, fixture.viewModel.state.value.devices.size)
            for (source in listOf(fixture.sourceA, fixture.sourceB)) {
                fixture.viewModel.requestClose(source.id)
                assertNull(fixture.viewModel.state.value.clearConfirmation)
                assertEquals(source, fixture.viewModel.requestClear(source))
            }
            assertEquals(TrafficSource.Other, fixture.viewModel.requestClear(TrafficSource.Other))
            assertNull(fixture.viewModel.requestClear(TrafficSource.All))
            fixture.viewModel.requestClose(RegisteredDeviceId("missing"))
            val notYetInRoster = TrafficSource.Device(RegisteredDeviceId("missing"))
            assertEquals(notYetInRoster, fixture.viewModel.requestClear(notYetInRoster))
            assertNull(fixture.viewModel.state.value.clearConfirmation)
            fixture.presenceState.value = emptyMap()
            runCurrent()
            assertEquals(2, fixture.viewModel.state.value.devices.size, "Connected tabs remain open after disconnect")
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `Close shell state cannot block toolbar Clear`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.viewModel.requestClose(fixture.a.id)
            assertNotNull(fixture.viewModel.state.value.clearConfirmation)
            assertEquals(fixture.sourceA, fixture.viewModel.requestClear(fixture.sourceA))
            fixture.viewModel.confirmClear()
            assertNotNull(fixture.viewModel.state.value.closingWorkspace)
            assertEquals(fixture.sourceB, fixture.viewModel.requestClear(fixture.sourceB))
            assertEquals(TrafficSource.Other, fixture.viewModel.requestClear(TrafficSource.Other))
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `reconnection during confirmation retains the target tab after its traffic is cleared`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.viewModel.requestClose(fixture.a.id)
            fixture.setPresence(fixture.a.id, DevicePresenceStatus.CONNECTED)
            runCurrent()
            assertEquals(fixture.sourceA, fixture.viewModel.confirmClear())
            fixture.viewModel.retainForClear(fixture.sourceA)
            fixture.retained.value -= fixture.a.id
            fixture.viewModel.finishClose(fixture.sourceA)
            runCurrent()
            assertEquals(DevicePresenceStatus.CONNECTED, fixture.viewModel.state.value.devices.first().status)
            assertEquals(fixture.sourceA, fixture.viewModel.state.value.source)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `reconnection during deletion prevents close and retains the workspace`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.viewModel.requestClose(fixture.a.id)
            assertEquals(fixture.sourceA, fixture.viewModel.confirmClear())
            fixture.viewModel.retainForClear(fixture.sourceA)
            fixture.retained.value -= fixture.a.id
            fixture.setPresence(fixture.a.id, DevicePresenceStatus.CONNECTED)
            runCurrent()
            fixture.viewModel.finishClose(fixture.sourceA)
            runCurrent()
            assertTrue(fixture.a.id in fixture.preferences.openDevices.first())
            assertEquals(DevicePresenceStatus.CONNECTED, fixture.viewModel.state.value.devices.first { it.device.id == fixture.a.id }.status)
            fixture.presenceState.value = emptyMap()
            runCurrent()
            assertTrue(fixture.viewModel.state.value.devices.any { it.device.id == fixture.a.id })
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `reconnect followed by disconnect during deletion still retains the workspace`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.viewModel.requestClose(fixture.a.id)
            assertEquals(fixture.sourceA, fixture.viewModel.confirmClear())
            fixture.viewModel.retainForClear(fixture.sourceA)
            fixture.retained.value -= fixture.a.id
            fixture.setPresence(fixture.a.id, DevicePresenceStatus.CONNECTED)
            runCurrent()
            fixture.presenceState.value = emptyMap()
            runCurrent()
            fixture.viewModel.finishClose(fixture.sourceA)
            runCurrent()
            assertTrue(fixture.viewModel.state.value.devices.any { it.device.id == fixture.a.id },
                "A reconnect observed during Close must keep the workspace even if the device disconnects again")
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `reconnect during confirmation is remembered after disconnect and confirmation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.viewModel.requestClose(fixture.a.id)
            fixture.setPresence(fixture.a.id, DevicePresenceStatus.CONNECTED)
            runCurrent()
            fixture.presenceState.value = emptyMap()
            runCurrent()
            assertEquals(true, fixture.viewModel.state.value.clearConfirmation?.hasReconnected)
            assertEquals(fixture.sourceA, fixture.viewModel.confirmClear())
            fixture.viewModel.retainForClear(fixture.sourceA)
            fixture.retained.value -= fixture.a.id
            fixture.viewModel.finishClose(fixture.sourceA)
            runCurrent()
            assertTrue(fixture.viewModel.state.value.devices.any { it.device.id == fixture.a.id })
            assertNull(fixture.viewModel.state.value.closingWorkspace)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `failed Close retains its tab and releases the operation for retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.viewModel.requestClose(fixture.a.id)
            assertEquals(fixture.sourceA, fixture.viewModel.confirmClear())
            fixture.viewModel.retainForClear(fixture.sourceA)
            fixture.viewModel.closeFailed()
            assertNull(fixture.viewModel.state.value.closingWorkspace)
            assertNotNull(fixture.viewModel.state.value.closeError)
            assertTrue(fixture.viewModel.state.value.devices.any { it.device.id == fixture.a.id })
            fixture.viewModel.dismissCloseError()
            fixture.viewModel.requestClose(fixture.a.id)
            assertEquals(fixture.sourceA, fixture.viewModel.state.value.clearConfirmation?.source)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `closing selected offline workspace falls back once and never steals selection on later reconnection`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            assertEquals(fixture.sourceA, fixture.viewModel.state.value.source)
            fixture.viewModel.requestClose(fixture.a.id)
            assertEquals(fixture.sourceA, fixture.viewModel.confirmClear())
            fixture.viewModel.retainForClear(fixture.sourceA)
            fixture.retained.value -= fixture.a.id
            fixture.viewModel.finishClose(fixture.sourceA)
            runCurrent()
            assertEquals(TrafficSource.Other, fixture.viewModel.state.value.source)
            assertEquals(TrafficSource.Other, fixture.selection.value)
            fixture.setPresence(fixture.a.id, DevicePresenceStatus.CONNECTED)
            runCurrent()
            assertEquals(TrafficSource.Other, fixture.viewModel.state.value.source)
            fixture.viewModel.select(fixture.sourceB)
            fixture.presenceState.value = emptyMap()
            runCurrent()
            assertEquals(fixture.sourceB, fixture.viewModel.state.value.source, "Removing a background tab cannot change selection")
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun `restored empty offline device falls back and later arrival does not auto select it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.retained.value = emptySet()
            runCurrent()
            assertEquals(TrafficSource.Other, fixture.viewModel.state.value.source)
            assertEquals(TrafficSource.Other, fixture.selection.value)
            fixture.setPresence(fixture.a.id, DevicePresenceStatus.CONNECTED)
            runCurrent()
            assertEquals(TrafficSource.Other, fixture.viewModel.state.value.source)
            fixture.viewModel.select(fixture.sourceA)
            assertEquals(fixture.sourceA, fixture.viewModel.state.value.source)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    private class Fixture {
        val a = RegisteredDevice(RegisteredDeviceId("a"), "Pixel", DeviceRegistrationKind.PAIRED_COMPANION, 1, 1)
        val b = a.copy(id = RegisteredDeviceId("b"), registeredAtEpochMillis = 2, lastSeenAtEpochMillis = 2)
        val sourceA = TrafficSource.Device(a.id)
        val sourceB = TrafficSource.Device(b.id)
        val retained = MutableStateFlow(setOf(a.id, b.id))
        val presenceState = MutableStateFlow<Map<RegisteredDeviceId, DevicePresence>>(emptyMap())
        val presence = object : DevicePresenceReader { override val presence = presenceState }
        val selection = MutableStateFlow<TrafficSource>(sourceA)
        val preferences = object : DeviceWorkspacePreferences {
            override val selectedSource = selection
            override suspend fun select(source: TrafficSource) { selection.value = source }
            override val openDevices = MutableStateFlow<Set<RegisteredDeviceId>>(emptySet())
            override suspend fun setOpen(id: RegisteredDeviceId, open: Boolean) { openDevices.value = if (open) openDevices.value + id else openDevices.value - id }
        }
        private val registry = object : RegisteredDeviceStore {
            override suspend fun putRegisteredDevice(device: RegisteredDevice) = error("Pairing must remain untouched")
            override suspend fun getRegisteredDevice(id: RegisteredDeviceId) = listOf(a, b).find { device -> device.id == id }
            override suspend fun markRegisteredDeviceSeen(id: RegisteredDeviceId, seenAtEpochMillis: Long) = error("Pairing must remain untouched")
            override suspend fun revokeRegisteredDevice(id: RegisteredDeviceId, revokedAtEpochMillis: Long) = error("Pairing must remain untouched")
            override fun observeRegisteredDevices() = flowOf(listOf(a, b))
        }
        private val history = object : DeviceTrafficHistory { override fun observeDeviceIdsWithTraffic() = retained }
        val viewModel = DeviceWorkspaceViewModel(ObserveDeviceWorkspacesUseCase(registry, presence, history, preferences),
            ObserveDeviceWorkspaceSelectionUseCase(preferences), SelectDeviceWorkspaceUseCase(preferences),
            ObserveSourceTrafficUseCase(SourceTrafficTelemetry { SourceTrafficSnapshot(0, 0, 0, 0) }), SetDeviceWorkspaceOpenUseCase(preferences))
        private val owner = ViewModelStore().also { it.put("workspace", viewModel) }
        fun setPresence(id: RegisteredDeviceId, status: DevicePresenceStatus) {
            presenceState.value += id to DevicePresence(status, "run-${id.value}", 1)
        }
        fun close() = owner.clear()
    }

}
