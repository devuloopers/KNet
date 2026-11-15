package com.devuloopers.knet.ui.desktop.httppanel.copy

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.application.usecase.traffic.PrepareStudioCopyUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Owner and latest-command fences surround the asynchronous clipboard write and its feedback revision. */
@OptIn(ExperimentalCoroutinesApi::class)
class StudioDetailsCopyViewModelTest {
    @Test fun `quick copy snapshots preset and owner and reports feedback only after clipboard success`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        val calls = mutableListOf<Pair<String, TrafficCopyPreset>>()
        val vm = StudioDetailsCopyViewModel(PrepareStudioCopyUseCase { id, preset -> calls += id to preset; "captured" }); store.put("copy", vm)
        try {
            vm.selectOwner("sent-A"); var clipboard = "old"
            vm.copy("sent-A", TrafficCopyPreset.RESPONSE_HEADERS) { clipboard = it }
            assertEquals(0, vm.state.value.copiedRevision); advanceUntilIdle()
            assertEquals("captured", clipboard); assertEquals(listOf("sent-A" to TrafficCopyPreset.RESPONSE_HEADERS), calls)
            assertEquals(1, vm.state.value.copiedRevision); assertNull(vm.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `new execution clear and owner disposal prevent old results from reaching the clipboard`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        val gate = CompletableDeferred<String>(); var clipboard = "old"
        val vm = StudioDetailsCopyViewModel(PrepareStudioCopyUseCase { _, _ -> withContext(NonCancellable) { gate.await() } }); store.put("copy", vm)
        try {
            vm.selectOwner("A"); vm.copy("A", TrafficCopyPreset.FULL) { clipboard = it }; runCurrent()
            vm.selectOwner("B"); gate.complete("stale"); advanceUntilIdle()
            vm.copy("A", TrafficCopyPreset.RESPONSE_BODY) { clipboard = it }; advanceUntilIdle()
            assertEquals("old", clipboard); assertEquals(0, vm.state.value.copiedRevision)
            vm.selectOwner(null); assertNull(vm.state.value.error); assertFalse(vm.state.value.copying)
            val second = CompletableDeferred<String>()
            val disposed = StudioDetailsCopyViewModel(PrepareStudioCopyUseCase { _, _ -> withContext(NonCancellable) { second.await() } }); store.put("late", disposed)
            disposed.selectOwner("A"); disposed.copy("A", TrafficCopyPreset.FULL) { clipboard = it }; runCurrent()
            store.clear(); second.complete("late"); advanceUntilIdle(); assertEquals("old", clipboard)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `latest command wins and failures preserve clipboard without successful copied feedback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore(); var clipboard = "old"
        val gates = listOf(CompletableDeferred<String>(), CompletableDeferred()); var index = 0
        val vm = StudioDetailsCopyViewModel(PrepareStudioCopyUseCase { _, _ -> withContext(NonCancellable) { gates[index++].await() } }); store.put("copy", vm)
        try {
            vm.selectOwner("A"); vm.copy("A", TrafficCopyPreset.RESPONSE_BODY) { clipboard = it }; runCurrent()
            vm.copy("A", TrafficCopyPreset.RESPONSE_COOKIES) { clipboard = it }; runCurrent()
            gates[1].complete("cookies"); runCurrent(); gates[0].complete("body"); advanceUntilIdle()
            assertEquals("cookies", clipboard); assertEquals(1, vm.state.value.copiedRevision)
            val failing = StudioDetailsCopyViewModel(PrepareStudioCopyUseCase { _, _ -> error("Response was cleared.") }); store.put("failure", failing)
            failing.selectOwner("A"); failing.copy("A", TrafficCopyPreset.FULL) { clipboard = it }; advanceUntilIdle()
            assertEquals("cookies", clipboard); assertEquals("Response was cleared.", failing.state.value.error); assertEquals(0, failing.state.value.copiedRevision)
            failing.selectOwner("B"); assertNull(failing.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun `clipboard failure is reported without success feedback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val viewModel = StudioDetailsCopyViewModel(PrepareStudioCopyUseCase { _, _ -> "prepared" })
        store.put("clipboard", viewModel)
        try {
            viewModel.selectOwner("A")
            viewModel.copy("A", TrafficCopyPreset.RESPONSE_BODY) { error("Clipboard unavailable.") }
            advanceUntilIdle()
            assertFalse(viewModel.state.value.copying)
            assertEquals(0, viewModel.state.value.copiedRevision)
            assertEquals("Clipboard unavailable.", viewModel.state.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
