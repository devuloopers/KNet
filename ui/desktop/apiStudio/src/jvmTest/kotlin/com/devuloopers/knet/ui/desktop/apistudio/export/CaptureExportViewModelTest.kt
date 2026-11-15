package com.devuloopers.knet.ui.desktop.apistudio.export

import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.usecase.traffic.*
import com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Deterministic export dialog ownership/cancellation tests with no platform picker or user files. */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureExportViewModelTest {
    private class Repository : CaptureExportRepository {
        val sources = mutableListOf<CaptureExportSource>()
        val released = mutableListOf<String>()
        val writes = mutableListOf<String>()
        var preparation: CompletableDeferred<Unit>? = null
        var writing: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun prepare(source: CaptureExportSource): PreparedCaptureExport {
            sources += source; preparation?.await()
            return PreparedCaptureExport((source as CaptureExportSource.Studio).id, 1, 1, 0, 0, 1)
        }
        override suspend fun write(prepared: PreparedCaptureExport, options: CaptureExportOptions, destination: String, progress: (CaptureExportProgress) -> Unit) {
            if (fail) error("Destination is full")
            writing?.await(); progress(CaptureExportProgress(1, 20)); writes += prepared.id
        }
        override suspend fun release(prepared: PreparedCaptureExport) { released += prepared.id }
    }
    private fun model(repository: Repository, picker: CaptureExportFilePicker = CaptureExportFilePicker { "chosen.knetcapture" }) = CaptureExportViewModel(
        PrepareCaptureExportUseCase(repository), SaveCaptureExportUseCase(repository, picker), ReleaseCaptureExportUseCase(repository))
    private suspend fun fixture(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun `save uses frozen original execution even while caller changes document`() = kotlinx.coroutines.test.runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository(); val model = model(repository)
            model.open(CaptureExportSource.Studio("run-A"), "Original request")
            advanceUntilIdle(); assertEquals("run-A", model.state.value.prepared?.id)
            model.save(); advanceUntilIdle()
            assertEquals(listOf("run-A"), repository.writes); assertTrue(model.state.value.complete)
            assertEquals(listOf("run-A"), repository.released)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `save dialog cancellation retains frozen snapshot for retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository(); var attempts = 0
            val model = model(repository, CaptureExportFilePicker { if (++attempts == 1) null else "chosen.knetcapture" })
            model.open(CaptureExportSource.Studio("run"), "Result"); advanceUntilIdle()
            model.save(); advanceUntilIdle()
            assertFalse(model.state.value.saving); assertFalse(model.state.value.complete); assertTrue(repository.released.isEmpty())
            model.save(); advanceUntilIdle(); assertTrue(model.state.value.complete); assertEquals(listOf("run"), repository.writes)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `destination error is visible and another save can retry without preparing mutable state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository(); val model = model(repository)
            model.open(CaptureExportSource.Studio("run"), "Result"); advanceUntilIdle()
            repository.fail = true; model.save(); advanceUntilIdle(); assertEquals("Destination is full", model.state.value.error)
            repository.fail = false; model.save(); advanceUntilIdle()
            assertTrue(model.state.value.complete); assertEquals(1, repository.sources.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `rapid replacement cancels original preparation without publishing wrong owner`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository().apply { preparation = CompletableDeferred() }; val model = model(repository)
            model.open(CaptureExportSource.Studio("old"), "Old"); runCurrent()
            repository.preparation = null
            model.open(CaptureExportSource.Studio("new"), "New"); advanceUntilIdle()
            assertEquals("new", model.state.value.prepared?.id); assertEquals("New", model.state.value.label)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `duplicate save is ignored while first writer owns snapshot`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository().apply { writing = CompletableDeferred() }; val model = model(repository)
            model.open(CaptureExportSource.Studio("run"), "Result"); advanceUntilIdle()
            model.save(); model.save(); runCurrent(); assertTrue(model.state.value.saving)
            repository.writing!!.complete(Unit); advanceUntilIdle()
            assertEquals(listOf("run"), repository.writes); assertEquals(20, model.state.value.progress?.bytesWritten)
        } finally { Dispatchers.resetMain() }
    }
}
