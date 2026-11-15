package com.devuloopers.knet.ui.desktop.traffic.importcapture

import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.importcapture.*
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.usecase.traffic.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Picker and native-drag lifetimes share one owner without disturbing captured device state. */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureImportDialogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun `Import opens a chooser without picking parsing or displaying historical traffic`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.vm.show(deviceA, "Phone A"); advanceUntilIdle()
            assertTrue(fixture.vm.state.value.visible)
            assertNull(fixture.vm.state.value.capture)
            assertEquals(deviceA, fixture.vm.state.value.workspace)
            assertEquals(0, fixture.picks); assertTrue(fixture.repo.opened.isEmpty())
            assertTrue(CaptureImportAction.entries.all { fixture.vm.unavailableReason(it) != null })
        } finally { fixture.close() }
    }

    @Test fun `drag entry renders before release and exit escape and drop end have distinct effects`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.vm.dragging(true)
            assertTrue(fixture.vm.state.value.dragging); assertFalse(fixture.vm.state.value.visible)
            assertTrue(fixture.repo.opened.isEmpty())
            fixture.vm.dragging(false); assertFalse(fixture.vm.state.value.dragging)
            fixture.vm.dragging(true); fixture.vm.dismiss()
            fixture.drop(listOf("cancelled"), deviceA, "Phone A"); advanceUntilIdle()
            assertTrue(fixture.repo.opened.isEmpty()); assertFalse(fixture.vm.state.value.visible)
            fixture.vm.dragging(false); fixture.vm.dragging(true)
            fixture.drop(listOf("accepted"), deviceA, "Phone A")
            fixture.vm.dragging(false) // Native onEnded follows onDrop; it must not hide the chooser.
            advanceUntilIdle()
            assertTrue(fixture.vm.state.value.visible); assertFalse(fixture.vm.state.value.dragging)
            assertNotNull(fixture.vm.state.value.capture); assertEquals(0, fixture.picks)
            fixture.vm.dismiss(); advanceUntilIdle()
        } finally { fixture.close() }
    }

    @Test fun `multiple drops malformed replacement and picker cancellation preserve the validated candidate`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.drop(listOf("first"), deviceA, "Phone A"); advanceUntilIdle()
            val first = assertNotNull(fixture.vm.state.value.capture)
            fixture.drop(listOf("one", "two"), deviceB, "Phone B"); advanceUntilIdle()
            assertEquals(first, fixture.vm.state.value.capture); assertEquals(1, fixture.repo.opened.size)
            assertNotNull(fixture.vm.state.value.error)
            fixture.repo.failOpen = true
            fixture.drop(listOf("bad"), deviceB, "Phone B"); advanceUntilIdle()
            assertEquals(first, fixture.vm.state.value.capture); assertTrue(fixture.repo.closed.isEmpty())
            fixture.repo.failOpen = false; fixture.pick = null
            fixture.vm.chooseFile(); advanceUntilIdle()
            assertEquals(first, fixture.vm.state.value.capture); assertTrue(fixture.vm.state.value.visible)
            assertEquals(deviceA, fixture.vm.state.value.workspace, "An open chooser keeps its original target")
            fixture.drop(listOf("replacement"), deviceB, "Phone B"); advanceUntilIdle()
            val replacement = assertNotNull(fixture.vm.state.value.capture)
            assertNotEquals(first.id, replacement.id); assertEquals(listOf(first.id), fixture.repo.closed)
            fixture.vm.dismiss(); advanceUntilIdle()
            assertEquals(listOf(first.id, replacement.id), fixture.repo.closed)
        } finally { fixture.close() }
    }

    @Test fun `cancelling after parse but before page publication closes only the new spool and cannot resurrect dialog`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            val blocked = CompletableDeferred<Unit>(); fixture.repo.pageBlock = blocked
            fixture.drop(listOf("pending"), deviceA, "Phone A"); runCurrent()
            val pending = fixture.repo.opened.single()
            fixture.vm.dismiss(); blocked.complete(Unit); advanceUntilIdle()
            assertFalse(fixture.vm.state.value.visible); assertNull(fixture.vm.state.value.capture)
            assertEquals(listOf(pending.id), fixture.repo.closed)
        } finally { fixture.close() }
    }

    @Test fun `bounded record pages and cancelled selection keep the newest chosen index`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.drop(listOf("history"), deviceA, "Phone A"); advanceUntilIdle()
            assertEquals(128, fixture.vm.state.value.rows.size)
            fixture.repo.recordBlock = CompletableDeferred()
            fixture.vm.select(1); runCurrent(); fixture.vm.select(2); runCurrent()
            assertEquals(2L, fixture.vm.state.value.selected?.index)
            fixture.repo.recordBlock!!.complete(Unit); advanceUntilIdle()
            assertEquals(2L, fixture.vm.state.value.selected?.index)
            fixture.vm.page(128); advanceUntilIdle()
            assertEquals(2, fixture.vm.state.value.rows.size); assertEquals(128L, fixture.vm.state.value.selected?.index)
            assertTrue(fixture.repo.limits.all { it == 128 })
            fixture.vm.dismiss(); advanceUntilIdle()
        } finally { fixture.close() }
    }

    @Test fun `every supported draft action retires private parsing data after successful handoff`() = runTest(dispatcher) {
        assertEquals(4, CaptureImportAction.entries.size)
        for (action in CaptureImportAction.entries) {
            val fixture = Fixture()
            try {
                fixture.drop(listOf("request"), deviceA, "Phone A"); advanceUntilIdle()
                val capture = assertNotNull(fixture.vm.state.value.capture)
                var draft: CaptureImportActionDraft? = null
                fixture.vm.perform(action) { draft = it }; advanceUntilIdle()
                assertNotNull(draft)
                assertEquals(listOf(capture.id), fixture.repo.closed)
                assertFalse(fixture.vm.state.value.presented)
            } finally { fixture.close() }
        }
    }

    @Test fun `editor handoff failure leaves the candidate retryable and never broadens workspace ownership`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.drop(listOf("request"), deviceA, "Phone A"); advanceUntilIdle()
            val capture = assertNotNull(fixture.vm.state.value.capture)
            fixture.vm.perform(CaptureImportAction.PROTECTED_TRAFFIC) { error("Finish the existing rule first.") }; advanceUntilIdle()
            assertTrue(fixture.vm.state.value.visible); assertEquals(capture, fixture.vm.state.value.capture)
            assertNotNull(fixture.vm.state.value.error); assertTrue(fixture.repo.closed.isEmpty())
            var result: CaptureImportActionDraft? = null
            fixture.vm.perform(CaptureImportAction.PROTECTED_TRAFFIC) { result = it }; advanceUntilIdle()
            assertEquals(deviceA, (result as CaptureImportActionDraft.ProtectedTraffic).workspace)
            assertEquals(listOf(capture.id), fixture.repo.closed)
        } finally { fixture.close() }
    }

    @Test fun `empty captures keep all four actions unavailable with an explanation`() = runTest(dispatcher) {
        val fixture = Fixture(); fixture.repo.count = 0
        try {
            fixture.drop(listOf("empty"), deviceA, "Phone A"); advanceUntilIdle()
            for (action in CaptureImportAction.entries) assertContains(assertNotNull(fixture.vm.unavailableReason(action)), "no records")
            fixture.vm.dismiss(); advanceUntilIdle()
        } finally { fixture.close() }
    }

    @Test fun `reserved drop keeps the surface alive through native end before queued paths arrive`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.vm.dragging(true)
            assertTrue(fixture.vm.state.value.presented)
            val token = assertNotNull(fixture.vm.beginDrop(deviceA, "Phone A"))
            assertTrue(fixture.vm.state.value.presented); assertTrue(fixture.vm.state.value.loading)
            fixture.vm.dragging(false)
            assertTrue(fixture.vm.state.value.presented); assertTrue(fixture.repo.opened.isEmpty())
            assertNull(fixture.vm.beginDrop(deviceB, "Phone B"), "An accepted handoff cannot be replaced while pending")
            fixture.vm.completeDrop(token, listOf("request")); advanceUntilIdle()
            assertTrue(fixture.vm.state.value.presented); assertEquals(deviceA, fixture.vm.state.value.workspace)
            fixture.vm.completeDrop(token, listOf("duplicate")); advanceUntilIdle()
            assertEquals(1, fixture.repo.opened.size)
            fixture.vm.dismiss(); advanceUntilIdle()
        } finally { fixture.close() }
    }

    @Test fun `dismissed native deliveries cannot reopen the overlay or replace a newer device reservation`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.vm.dragging(true)
            val old = assertNotNull(fixture.vm.beginDrop(deviceA, "Phone A"))
            fixture.vm.dismiss(); fixture.vm.dragging(false)
            fixture.vm.completeDrop(old, listOf("late")); fixture.vm.rejectDrop(old, "late error")
            assertFalse(fixture.vm.state.value.presented); assertTrue(fixture.repo.opened.isEmpty())
            val current = assertNotNull(fixture.vm.beginDrop(deviceB, "Phone B"))
            fixture.vm.completeDrop(old, listOf("older")); fixture.vm.rejectDrop(old, "older error")
            assertTrue(fixture.vm.state.value.loading); assertNull(fixture.vm.state.value.error)
            fixture.vm.completeDrop(current, listOf("new")); advanceUntilIdle()
            assertEquals(deviceB, fixture.vm.state.value.workspace); assertEquals(1, fixture.repo.opened.size)
            fixture.vm.dismiss(); advanceUntilIdle()
        } finally { fixture.close() }
    }

    @Test fun `native transfer rejection updates the same surface while preserving the validated candidate`() = runTest(dispatcher) {
        val fixture = Fixture()
        try {
            fixture.drop(listOf("first"), deviceA, "Phone A"); advanceUntilIdle()
            val first = fixture.vm.state.value.capture
            val token = assertNotNull(fixture.vm.beginDrop(deviceB, "Phone B"))
            fixture.vm.dragging(false); fixture.vm.rejectDrop(token, "Only local files can be imported.")
            assertTrue(fixture.vm.state.value.presented); assertFalse(fixture.vm.state.value.loading)
            assertEquals(first, fixture.vm.state.value.capture); assertEquals(deviceA, fixture.vm.state.value.workspace)
            assertNotNull(fixture.vm.state.value.error)
            fixture.vm.dismiss(); advanceUntilIdle()
        } finally { fixture.close() }
    }

    @Test fun `clearing the overlay owner invalidates a reserved native transfer before file access`() = runTest(dispatcher) {
        val fixture = Fixture()
        val token = assertNotNull(fixture.vm.beginDrop(deviceA, "Phone A"))
        fixture.store.clear()
        fixture.vm.completeDrop(token, listOf("late")); fixture.vm.rejectDrop(token, "late error")
        advanceUntilIdle()
        assertFalse(fixture.vm.state.value.presented); assertTrue(fixture.repo.opened.isEmpty())
    }

    private class Fixture {
        val repo = Repository(); var picks = 0; var pick: String? = "picked"
        val store = ViewModelStore()
        val vm = CaptureImportDialogViewModel(OpenCaptureImportUseCase(repo, CaptureImportFilePicker { picks++; pick }),
            ReadCaptureImportPageUseCase(repo), ReadCaptureImportRecordUseCase(repo),
            PrepareCaptureImportActionUseCase(repo, BreakpointProtocolRegistry(), NetworkConditionProtocolRegistry()), CloseCaptureImportUseCase(repo))
        init { store.put("dialog", vm) }
        fun drop(paths: List<String>, workspace: PolicyWorkspace, name: String) {
            vm.beginDrop(workspace, name)?.let { vm.completeDrop(it, paths) }
        }
        fun close() { vm.dismiss(); store.clear() }
    }
    private class Repository : CaptureImportRepository {
        var failOpen = false; var count = 130L
        var pageBlock: CompletableDeferred<Unit>? = null; var recordBlock: CompletableDeferred<Unit>? = null
        val opened = mutableListOf<ImportedCapture>(); val closed = mutableListOf<String>(); val limits = mutableListOf<Int>()
        override suspend fun open(path: String, progress: (CaptureImportProgress) -> Unit): ImportedCapture {
            if (failOpen) error("Malformed capture")
            return ImportedCapture("${path}-${opened.size}", "$path.har", CaptureImportFormat.HAR, count, 0, emptyList()).also { opened += it }
        }
        override suspend fun page(id: String, offset: Long, limit: Int): List<ImportedRecord> {
            limits += limit; pageBlock?.let { withContext(NonCancellable) { it.await() } }
            return (offset until minOf(count, offset + limit)).map { row(it, false) }
        }
        override suspend fun record(id: String, index: Long): ImportedRecord {
            if (index == 1L) recordBlock?.let { withContext(NonCancellable) { it.await() } }
            return row(index, true)
        }
        override suspend fun body(id: String, body: ImportedBody, limit: Int) = ImportedBodyPreview(byteArrayOf(), false)
        override suspend fun close(id: String) { closed += id }
        private fun row(index: Long, details: Boolean) = ImportedRecord(index, "http", "GET", "https://import.test/$index", 200, 1, 1.0,
            if (details) ImportedMessage(emptyList(), ImportedBody(null, ImportedBodyState.EMPTY, 0, true)) else null, null,
            "{\"deviceId\":\"untrusted-file-identity\"}")
    }
    private companion object {
        val deviceA = PolicyWorkspace.Device(RegisteredDeviceId("device-a"))
        val deviceB = PolicyWorkspace.Device(RegisteredDeviceId("device-b"))
    }
}
