package com.devuloopers.knet.ui.desktop.app.workspace

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import com.devuloopers.knet.application.contract.device.*
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.identity.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Measures actual composed tabs at compact/wide widths; the native window is never launched. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class DeviceWorkspacePresentationTest {
    @Test fun `same names and suffixes remain distinct while a new identity creates another tab`() {
        val entries = listOf(entry("one-abcdef", DevicePresenceStatus.CONNECTED), entry("two-abcdef", DevicePresenceStatus.OFFLINE))
        val labels = deviceWorkspaceLabels(entries)
        assertEquals(2, labels.values.toSet().size)
        assertEquals(labels, deviceWorkspaceLabels(entries.reversed()))
        assertEquals(3, deviceWorkspaceLabels(entries + entry("three-abcdef", DevicePresenceStatus.RECONNECTING)).size)
    }

    @Test fun `long offline tabs overflow horizontally without wrapping or hiding native controls`() = onUiThread {
        for (width in listOf(420, 1400)) ImageComposeScene(width, 100, content = {
            KNetTheme {
                DeviceWorkspaceTabs(DeviceWorkspaceState(devices = List(12) { entry("device-$it", DevicePresenceStatus.OFFLINE) },
                    source = TrafficSource.Device(RegisteredDeviceId("device-0"))), {})
            }
        }).use { scene ->
            scene.render(1_000_000)
            val root = scene.semanticsOwners.first().rootSemanticsNode
            fun nodes(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
            val tabs = nodes(root).filter { it.config.getOrNull(SemanticsProperties.Selected) != null }
            assertTrue(tabs.isNotEmpty())
            assertEquals(1, tabs.count { it.config.getOrNull(SemanticsProperties.Selected) == true })
            assertTrue(tabs.filter { it.boundsInRoot.width > 0 }.all { it.boundsInRoot.height <= 40f })
            assertTrue(tabs.any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { label -> "offline" in label } == true })
        }
    }

    @Test fun `Close is an independent accessible button inside native interactive tab bounds`() = onUiThread {
        val onlineId = RegisteredDeviceId("8A41D2")
        val offlineId = RegisteredDeviceId("491BE3")
        val onlineSource = TrafficSource.Device(onlineId)
        val selected = mutableStateOf<TrafficSource>(onlineSource)
        var closed: RegisteredDeviceId? = null
        var bounds = emptyList<androidx.compose.ui.geometry.Rect>()
        ImageComposeScene(1800, 80, density = Density(2f), content = {
            KNetTheme {
                DeviceWorkspaceTabs(DeviceWorkspaceState(devices = listOf(
                    entry(onlineId.value, DevicePresenceStatus.CONNECTED, "Pixel 8"),
                    entry("F72C10", DevicePresenceStatus.CONNECTED, "iPhone 15"),
                    entry(offlineId.value, DevicePresenceStatus.OFFLINE, "Galaxy A50"),
                ), source = selected.value), onSelect = { source -> selected.value = source }, onClose = { id -> closed = id },
                    onInteractiveBoundsChanged = { bounds = it })
            }
        }).use { scene ->
            scene.render(1_000_000)
            scene.render(1_500_000).use { frame ->
                val directory = File("build/reports/device-workspace-tabs").apply { mkdirs() }
                frame.encodeToData()?.use { data -> File(directory, "compact-tabs.png").writeBytes(data.bytes) }
            }
            fun nodes(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
                listOf(node) + node.children.flatMap(::nodes)
            val buttons = nodes(scene.semanticsOwners.first().rootSemanticsNode)
                .filter { node -> node.config.getOrNull(SemanticsProperties.Role) == Role.Button }
            assertEquals(3, buttons.size, "Other traffic has no close button")
            val online = buttons.filter { node -> node.config.getOrNull(SemanticsProperties.Disabled) != null }
            assertEquals(2, online.size)
            assertTrue(online.all { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)!!.single().contains("Connected devices remain visible") })
            val offline = buttons.single { node -> node.config.getOrNull(SemanticsProperties.Disabled) == null }
            assertEquals(true, offline.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke())
            assertEquals(offlineId, closed)
            assertEquals(onlineSource, selected.value, "Closing a background tab must not select it")
            assertTrue(bounds.any { rectangle -> rectangle.contains(offline.boundsInRoot.center) },
                "Native title-bar hit testing must treat Close as an interactive control")
            closed = null
            val position = offline.boundsInWindow.center
            scene.sendPointerEvent(PointerEventType.Move, position)
            scene.sendPointerEvent(PointerEventType.Press, position,
                buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, position, buttons = PointerButtons(), button = PointerButton.Primary)
            scene.render(2_000_000)
            assertEquals(offlineId, closed)
            assertEquals(onlineSource, selected.value, "Pointer Close must consume the click before the parent tab")
        }
    }
    @Test fun `only the matching Companion suffix is removed and colliding desktop suffixes stay unique`() {
        val first = entry("install-6Zwj2h", DevicePresenceStatus.CONNECTED, "samsung SM-A505F · WJ2H")
        val second = entry("other--6Zwj2h", DevicePresenceStatus.OFFLINE, "samsung SM-A505F · WJ2H")
        val labels = deviceWorkspaceTabLabels(listOf(first, second))
        assertEquals(setOf("samsung SM-A505F"), labels.values.map { it.name }.toSet())
        assertEquals(2, labels.values.map { it.fullLabel }.toSet().size)
        assertTrue(labels.values.all { it.fullLabel.count { character -> character == '·' } == 1 })
        assertEquals(labels, deviceWorkspaceTabLabels(listOf(second, first)))
        assertEquals(labels.mapValues { it.value.fullLabel }, deviceWorkspaceLabels(listOf(first, second)))
        val custom = first.copy(device = first.device.copy(displayName = "QA lab · ABCD"))
        assertEquals("QA lab · ABCD", deviceWorkspaceTabLabels(listOf(custom)).getValue(first.device.id).name)
        val alreadyClean = first.copy(device = first.device.copy(displayName = "samsung SM-A505F"))
        assertEquals("samsung SM-A505F · 6Zwj2h", deviceWorkspaceLabels(listOf(alreadyClean)).getValue(first.device.id))
        assertEquals("samsung SM-A505F · WJ2H", first.device.displayName, "Stored registration must not be changed")
    }

    @Test fun `tab widths follow names and cap long labels without wrapping`() = onUiThread {
        val samsung = entry("install-6Zwj2h", DevicePresenceStatus.CONNECTED, "samsung SM-A505F · WJ2H")
        val mediumName = "Samsung Galaxy SM-A505F QA"
        val longName = "Company physical testing Samsung SM-A505F device with a very long display name"
        val medium = entry("device-abcdef", DevicePresenceStatus.CONNECTED, mediumName)
        val long = entry("device-123456", DevicePresenceStatus.CONNECTED, longName)
        ImageComposeScene(1500, 100, content = {
            KNetTheme { DeviceWorkspaceTabs(DeviceWorkspaceState(devices = listOf(samsung, medium, long)), {}) }
        }).use { scene ->
            scene.render(1_000_000).close()
            fun tab(id: RegisteredDeviceId) = scene.allNodes().single { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { id.value.takeLast(6) in it }
            }
            val samsungLayout = scene.layoutFor("samsung SM-A505F")
            assertTrue(samsungLayout.multiParagraph.intrinsics.maxIntrinsicWidth <= samsungLayout.size.width + 1f,
                "The Samsung model should fit in full, allowing pixel rounding")
            assertEquals(1, samsungLayout.lineCount)
            val mediumLayout = scene.layoutFor(mediumName)
            assertTrue(mediumLayout.multiParagraph.intrinsics.maxIntrinsicWidth <= mediumLayout.size.width + 1f)
            assertEquals(1, mediumLayout.lineCount)
            scene.render(2_000_000).use { frame ->
                val directory = File("build/reports/device-workspace-tabs").apply { mkdirs() }
                frame.encodeToData()?.use { data -> File(directory, "label-widths.png").writeBytes(data.bytes) }
            }
            assertTrue(tab(medium.device.id).boundsInRoot.width > 280f, "A name that needs more room should grow past the old cap")
            assertTrue(tab(samsung.device.id).boundsInRoot.width < tab(medium.device.id).boundsInRoot.width)
            val longLayout = scene.layoutFor(longName)
            // Simple Text recreates a layout for semantics; measure constrained space rather than
            // relying on its ellipsis flag. The rendered snapshot verifies the painted ellipsis.
            assertTrue(longLayout.multiParagraph.intrinsics.maxIntrinsicWidth > longLayout.size.width,
                "An oversized name should remain constrained instead of widening the tab")
            assertEquals(1, longLayout.lineCount)
            assertTrue(tab(long.device.id).boundsInRoot.width <= 360f)
        }
    }

    @Test fun `full label help opens on name hover and disappears on exit without overlapping Close help`() = onUiThread {
        val clock = TestCoroutineScheduler()
        val name = "Company physical testing Samsung SM-A505F device with a very long display name"
        val device = entry("install-6Zwj2h", DevicePresenceStatus.OFFLINE, "$name · WJ2H")
        val label = "$name · 6Zwj2h"
        ImageComposeScene(1000, 220, coroutineContext = UnconfinedTestDispatcher(clock), content = {
            KNetTheme { DeviceWorkspaceTabs(DeviceWorkspaceState(devices = listOf(device)), {}) }
        }).use { scene ->
            var frameNanos = 0L
            fun frame() { scene.render(++frameNanos).close() }
            frame()
            val mainOwner = scene.semanticsOwners.single()
            fun popupText() = scene.semanticsOwners.filter { it != mainOwner }
                .flatMap { owner -> descendants(owner.rootSemanticsNode) }
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
            val tab = scene.allNodes().single { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { "6Zwj2h" in it }
            }
            scene.sendPointerEvent(PointerEventType.Move, tab.boundsInWindow.center)
            frame(); clock.advanceTimeBy(500); clock.runCurrent(); frame()
            assertTrue(label in popupText(), "Hover should show the complete untruncated label with one ID")
            scene.sendPointerEvent(PointerEventType.Move, Offset(999f, 219f))
            clock.runCurrent(); frame()
            assertTrue(popupText().isEmpty(), "Leaving the name must remove its tooltip immediately")
            val close = scene.allNodes().single { node -> node.config.getOrNull(SemanticsProperties.Role) == Role.Button }
            scene.sendPointerEvent(PointerEventType.Move, close.boundsInWindow.center)
            frame(); clock.advanceTimeBy(500); clock.runCurrent(); frame()
            assertEquals(1, scene.semanticsOwners.count { it != mainOwner }, "Only one help popup may be visible")
            assertTrue(popupText().any { it.startsWith("Clear traffic and close ") })
            assertFalse(label in popupText(), "Name help must not overlap the Close tooltip")
        }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    private fun ImageComposeScene.allNodes() = semanticsOwners.flatMap { owner -> descendants(owner.rootSemanticsNode) }
    private fun ImageComposeScene.layoutFor(text: String): TextLayoutResult {
        // Read the text node itself; merged tab semantics can expose a sibling's layout callback.
        for (node in semanticsOwners.flatMap { owner -> descendants(owner.unmergedRootSemanticsNode) }) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.firstOrNull { it.layoutInput.text.text == text }?.let { return it }
        }
        error("Text layout missing: $text")
    }

    private fun entry(id: String, status: DevicePresenceStatus, name: String = "A very long shared device display name") = DeviceWorkspaceEntry(
        RegisteredDevice(RegisteredDeviceId(id), name, DeviceRegistrationKind.PAIRED_COMPANION, 1, 1), status)
    private fun onUiThread(block: () -> Unit) {
        if (EventQueue.isDispatchThread()) block() else {
            var failure: Throwable? = null
            EventQueue.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
            failure?.let { throw it }
        }
    }
}
