package com.devuloopers.knet.ui.desktop.traffic.copy

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.export.CaptureExportSource
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficDetailsUseCase
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.id.ExchangeId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Clipboard callbacks publish once for the intended source without retaining copied payloads in UI state. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficDetailsCopyViewModelTest {
    @Test fun `latest copy wins even if a cancelled preparation returns late`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        val gates = listOf(CompletableDeferred<String>(), CompletableDeferred()); var calls = 0; val copied = mutableListOf<String>()
        val vm = TrafficDetailsCopyViewModel(PrepareTrafficDetailsUseCase { _, _ -> withContext(NonCancellable) { gates[calls++].await() } }); store.put("copy", vm)
        try {
            vm.copy(request()) { copied += it }; runCurrent()
            vm.copy(request()) { copied += it }; runCurrent()
            gates[1].complete("second"); runCurrent(); gates[0].complete("first"); advanceUntilIdle()
            assertEquals(listOf("second"), copied); assertFalse(vm.state.value.copying)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `source switch cancels pending publication and rejects an old menu callback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        val gate = CompletableDeferred<String>(); var copied = "unchanged"
        val vm = TrafficDetailsCopyViewModel(PrepareTrafficDetailsUseCase { _, _ -> withContext(NonCancellable) { gate.await() } }); store.put("copy", vm)
        try {
            vm.selectSource(TrafficSource.Other); vm.copy(request()) { copied = it }; runCurrent()
            vm.selectSource(TrafficSource.Device(RegisteredDeviceId("other")))
            gate.complete("old device"); advanceUntilIdle()
            vm.copy(request()) { copied = it }; advanceUntilIdle()
            assertEquals("unchanged", copied); assertFalse(vm.state.value.copying); assertNull(vm.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `selection is copied before suspension and clipboard errors use the existing feedback state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        var received = emptyList<TrafficRecordId>()
        val vm = TrafficDetailsCopyViewModel(PrepareTrafficDetailsUseCase { source, _ -> received = source.records; "formatted" }); store.put("copy", vm)
        try {
            val records = mutableListOf<TrafficRecordId>(TrafficRecordId.Exchange(ExchangeId("original")))
            vm.copy(CaptureExportSource.Selection(records, TrafficSource.Other)) { throw IllegalStateException("Clipboard unavailable") }
            records.clear(); records += TrafficRecordId.Exchange(ExchangeId("later")); advanceUntilIdle()
            assertEquals("original", (received.single() as TrafficRecordId.Exchange).id.value)
            assertEquals("Clipboard unavailable", vm.state.value.error); assertFalse(vm.state.value.copying)
            vm.clearError(); assertNull(vm.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `preparation failure preserves clipboard and cleared owner cannot publish late`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore(); var copied = "old"
        val vm = TrafficDetailsCopyViewModel(PrepareTrafficDetailsUseCase { _, _ -> error("Select fewer items or use Export.") }); store.put("copy", vm)
        try {
            vm.copy(request()) { copied = it }; advanceUntilIdle()
            assertEquals("old", copied); assertContains(vm.state.value.error.orEmpty(), "Export")
            val gate = CompletableDeferred<String>()
            val late = TrafficDetailsCopyViewModel(PrepareTrafficDetailsUseCase { _, _ -> withContext(NonCancellable) { gate.await() } }); store.put("late", late)
            late.copy(request()) { copied = it }; runCurrent(); store.clear(); gate.complete("late"); advanceUntilIdle()
            assertEquals("old", copied)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `explicit preset is frozen with source and reaches the application boundary`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        val received = mutableListOf<TrafficCopyPreset>()
        val vm = TrafficDetailsCopyViewModel(PrepareTrafficDetailsUseCase { _, preset -> received += preset; preset.name }); store.put("copy", vm)
        try {
            var clipboard = ""
            vm.copy(request(), TrafficCopyPreset.RESPONSE_BASE64) { clipboard = it }; advanceUntilIdle()
            assertEquals(listOf(TrafficCopyPreset.RESPONSE_BASE64), received)
            assertEquals("RESPONSE_BASE64", clipboard)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    private fun request() = CaptureExportSource.Selection(listOf(TrafficRecordId.Exchange(ExchangeId("one"))), TrafficSource.Other)
}
