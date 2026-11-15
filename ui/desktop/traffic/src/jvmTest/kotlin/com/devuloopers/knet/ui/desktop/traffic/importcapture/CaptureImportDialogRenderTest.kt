package com.devuloopers.knet.ui.desktop.traffic.importcapture

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.importcapture.*
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.usecase.traffic.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Actual shell scenes verify one surface through native handoff and parsing, without an OS dialog. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureImportDialogRenderTest {
    private var time = 0L

    @Test fun `drag handoff validation ready and errors retain the exact surface heading and workspace instance`() = renderTest { fixture ->
        var mounts = 0; var disposals = 0
        scene(fixture, background = { DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }; Text("Captured Traffic") }).use { scene ->
            scene.frames(); fixture.vm.dragging(true); scene.frames()
            val surface = scene.pane(); val heading = scene.label("Drop files into KNet")
            val surfaceId = surface.id; val bounds = surface.boundsInRoot; val headingId = heading.id; val headingBounds = heading.boundsInRoot
            assertTrue(scene.has("Release to validate the file and choose an action."))
            assertEquals(0, fixture.repo.opens); assertEquals(0, fixture.picks)
            fixture.repo.openGate = CompletableDeferred()
            val token = assertNotNull(fixture.vm.beginDrop(PolicyWorkspace.Other, "Other traffic"))
            scene.frames(1); scene.assertSamePane(surfaceId, bounds, headingId, headingBounds)
            fixture.vm.dragging(false) // onEnded precedes native path delivery.
            scene.frames(1); scene.assertSamePane(surfaceId, bounds, headingId, headingBounds)
            fixture.vm.completeDrop(token, listOf("session")); scene.frames()
            scene.assertSamePane(surfaceId, bounds, headingId, headingBounds); assertTrue(scene.has("Validating capture"))
            assertFalse(scene.has("Esc  Cancel"))
            fixture.repo.openGate!!.complete(Unit); scene.frames()
            scene.assertSamePane(surfaceId, bounds, headingId, headingBounds); assertTrue(scene.has("session.har"))
            assertFalse(scene.has("Open in Traffic")); assertFalse(scene.has("Default"))
            assertEquals(1, scene.semanticsOwners.size, "No second native/Compose dialog layer is created")
            val replacement = assertNotNull(fixture.vm.beginDrop(PolicyWorkspace.Other, "Other traffic"))
            fixture.vm.dragging(false); fixture.vm.rejectDrop(replacement, "Only local files can be imported."); scene.frames()
            scene.assertSamePane(surfaceId, bounds, headingId, headingBounds); assertTrue(scene.has("Only local files can be imported."))
            assertEquals(1, mounts); assertEquals(0, disposals)
            scene.save("/tmp/knet-continuous-import-ready.png")
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)); scene.frames()
            assertFalse(fixture.vm.state.value.presented); assertTrue(scene.has("Captured Traffic"))
            assertEquals(1, mounts); assertEquals(0, disposals)
        }
    }

    @Test fun `leave and Escape remove only a preview and queued paths cannot resurrect a dismissed overlay`() = renderTest { fixture ->
        scene(fixture).use { scene ->
            fixture.vm.dragging(true); scene.frames(); assertTrue(scene.has("Drop files into KNet"))
            fixture.vm.dragging(false); scene.frames(); assertFalse(scene.has("Drop files into KNet"))
            fixture.vm.dragging(true); scene.frames()
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)); scene.frames()
            assertNull(fixture.vm.beginDrop(PolicyWorkspace.Other, "Other traffic"))
            fixture.vm.dragging(false)
            val token = assertNotNull(fixture.vm.beginDrop(PolicyWorkspace.Other, "Other traffic")); scene.frames()
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)); scene.frames()
            fixture.vm.completeDrop(token, listOf("late")); scene.frames()
            assertFalse(scene.has("Drop files into KNet")); assertEquals(0, fixture.repo.opens)
        }
    }

    @Test fun `Import uses the same surface and four disabled actions at narrow and Retina sizes`() = renderTest { fixture ->
        for (density in listOf(1f, 2f)) for (width in listOf(380, 1100)) {
            fixture.vm.show(PolicyWorkspace.Other, "Other traffic")
            scene(fixture, width, 950, Density(density, 1.25f)).use { scene ->
                scene.frames(); val id = scene.pane().id
                assertTrue(scene.has("Choose file")); assertTrue(scene.has("Available after validation"))
                assertFalse(scene.has("Esc  Dismiss")); assertFalse(scene.has("Esc  Cancel"))
                assertFalse(scene.has("Open in Traffic")); assertFalse(scene.has("Back to live Traffic"))
                for (label in actionLabels) assertTrue(scene.has(label))
                scene.click("Send to API Studio"); scene.frames(); assertTrue(fixture.drafts.isEmpty())
                val picks = fixture.picks
                scene.click("Choose file"); scene.frames()
                assertEquals(picks + 1, fixture.picks); assertEquals(id, scene.pane().id)
                assertTrue(fixture.vm.state.value.presented)
                val close = scene.nodes().single { "Dismiss import" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                scene.clickAt(close.boundsInWindow.center); scene.frames()
                assertFalse(fixture.vm.state.value.presented)
            }
            fixture.vm.dismiss()
        }
    }

    @Test fun `ready overlay pages a bounded record list and each of the four actions prepares only its chosen draft`() = renderTest { fixture ->
        for (label in actionLabels) {
            fixture.drop(); fixture.repo.count = 130
            // Replace with the large capture so the real page controls are exercised too.
            fixture.drop()
            scene(fixture, height = 1100).use { scene ->
                scene.frames(); assertTrue(scene.has("1–128 of 130"))
                scene.click("Next"); scene.frames(); assertTrue(scene.has("129–130 of 130"))
                val count = fixture.drafts.size
                scene.click(label); scene.frames()
                assertEquals(count + 1, fixture.drafts.size); assertFalse(fixture.vm.state.value.presented)
            }
        }
        assertIs<CaptureImportActionDraft.ApiStudio>(fixture.drafts[0])
        assertIs<CaptureImportActionDraft.Intercept>(fixture.drafts[1])
        assertIs<CaptureImportActionDraft.NetworkConditions>(fixture.drafts[2])
        assertIs<CaptureImportActionDraft.ProtectedTraffic>(fixture.drafts[3])
    }

    @Test fun `surface consumes blank inside hits keeps keyboard focus and outside clicks never activate the workspace`() = renderTest { fixture ->
        var backgroundFocused = false
        scene(fixture, background = {
            BasicTextField("background", {}, Modifier.onFocusChanged { backgroundFocused = it.hasFocus })
        }).use { scene ->
            fixture.vm.show(PolicyWorkspace.Other, "Other traffic"); scene.frames()
            repeat(24) { scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyUp)); scene.frames(1) }
            assertFalse(backgroundFocused)
            val bounds = scene.pane().boundsInWindow
            scene.clickAt(Offset(bounds.right - 8, bounds.bottom - 8)); scene.frames()
            assertTrue(fixture.vm.state.value.presented, "A hit inside the surface must not activate the scrim")
            scene.clickAt(Offset(4f, 4f)); scene.frames()
            assertFalse(fixture.vm.state.value.presented); assertFalse(backgroundFocused)
        }
    }

    private fun renderTest(block: (Fixture) -> Unit) = EventQueue.invokeAndWait {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.vm.dismiss(); fixture.store.clear(); Dispatchers.resetMain() }
    }
    private fun scene(fixture: Fixture, width: Int = 1280, height: Int = 960, density: Density = Density(1f), background: @Composable () -> Unit = { Text("Captured Traffic") }) =
        ImageComposeScene((width * density.density).toInt(), (height * density.density).toInt(), density, content = { KNetTheme {
            CaptureImportDropHost(fixture.vm, PolicyWorkspace.Other, "Other traffic", fixture.drafts::add, Modifier.fillMaxSize()) { background() }
        } })
    private fun ImageComposeScene.frames(count: Int = 8) { repeat(count) { render(time.also { time += 100_000_000 }).close() } }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        return semanticsOwners.flatMap { descend(it.rootSemanticsNode) }
    }
    private fun ImageComposeScene.has(value: String) = nodes().any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }
    private fun ImageComposeScene.label(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }
    private fun ImageComposeScene.pane() = nodes().single { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Import capture" }
    private fun ImageComposeScene.assertSamePane(id: Int, bounds: androidx.compose.ui.geometry.Rect, headingId: Int, headingBounds: androidx.compose.ui.geometry.Rect) {
        assertEquals(id, pane().id); assertEquals(bounds, pane().boundsInRoot)
        assertEquals(headingId, label("Drop files into KNet").id)
        assertEquals(headingBounds, label("Drop files into KNet").boundsInRoot)
    }
    private fun ImageComposeScene.click(value: String) = clickAt(label(value).boundsInWindow.center)
    private fun ImageComposeScene.clickAt(position: Offset) {
        sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, button = PointerButton.Primary)
    }
    private fun ImageComposeScene.save(path: String) = render(time.also { time += 100_000_000 }).use { image -> image.encodeToData()!!.use { File(path).writeBytes(it.bytes) } }
    private class Fixture {
        val repo = Repository(); val store = ViewModelStore(); val drafts = mutableListOf<CaptureImportActionDraft>(); var picks = 0
        val vm = CaptureImportDialogViewModel(OpenCaptureImportUseCase(repo, CaptureImportFilePicker { picks++; null }),
            ReadCaptureImportPageUseCase(repo), ReadCaptureImportRecordUseCase(repo),
            PrepareCaptureImportActionUseCase(repo, BreakpointProtocolRegistry(), NetworkConditionProtocolRegistry()), CloseCaptureImportUseCase(repo))
        init { store.put("overlay", vm) }
        fun drop() { vm.beginDrop(PolicyWorkspace.Other, "Other traffic")?.let { vm.completeDrop(it, listOf("session")) } }
    }
    private class Repository : CaptureImportRepository {
        var opens = 0; var count = 1L; var openGate: CompletableDeferred<Unit>? = null
        override suspend fun open(path: String, progress: (CaptureImportProgress) -> Unit): ImportedCapture {
            opens++; openGate?.await(); return ImportedCapture("candidate-$opens", "$path.har", CaptureImportFormat.HAR, count, 0, emptyList())
        }
        override suspend fun page(id: String, offset: Long, limit: Int) = (offset until minOf(count, offset + limit)).map(::record)
        override suspend fun record(id: String, index: Long) = record(index)
        private fun record(index: Long) = ImportedRecord(index, "http", "GET", "https://import.test/$index", 200, 1, 1.0,
            ImportedMessage(emptyList(), ImportedBody(null, ImportedBodyState.EMPTY, 0, true)), null, "{}")
        override suspend fun body(id: String, body: ImportedBody, limit: Int) = ImportedBodyPreview(byteArrayOf(), false)
        override suspend fun close(id: String) = Unit
    }
    private companion object {
        val actionLabels = listOf("Send to API Studio", "Create Intercept rule", "Create Network Conditions rule", "Create Protected Traffic rule")
    }
}
