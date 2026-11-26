package com.devuloopers.knet.ui.desktop.traffic.toolbar

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.devuloopers.knet.ui.core.components.button.KNetButton
import java.io.File
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.use
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.CaptureState
import java.awt.EventQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.*

/** Narrow windows retain file/capture actions in the theme's scroll lane without moving trailing controls. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class TrafficFileActionsRenderTest {
    @Test fun `import and selected export remain reachable with fixed auto scroll controls`() = EventQueue.invokeAndWait {
        for (width in listOf(900, 1440)) {
            var exported = false; var imported = false
            ImageComposeScene(width, 100, coroutineContext = UnconfinedTestDispatcher(), content = { KNetTheme {
                TrafficToolbar(TrafficToolbarState(exportAvailable = true, selectedCount = 3),
                    TrafficToolbarActions(onImport = { imported = true }, onExport = { exported = true }),
                    networkConditionsControl = { Text("Conditions: Off", color = KNetTheme.colors.textSecondary, maxLines = 1, softWrap = false) })
            } }).use { scene ->
                var time = 0L
                fun frame() { scene.render(time.also { time += 250_000_000 }).close() }
                fun nodes(): List<SemanticsNode> {
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    return scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                }
                fun label(text: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { label -> label.text == text } }
                fun click(text: String) {
                    val center = label(text).boundsInRoot.center
                    nodes().filter { it.config.contains(SemanticsActions.OnClick) && it.boundsInRoot.contains(center) }
                        .minBy { it.boundsInRoot.width * it.boundsInRoot.height }.config[SemanticsActions.OnClick].action!!.invoke()
                }
                repeat(10) { frame() }
                val auto = label("Auto scroll").boundsInRoot
                assertTrue(auto.left > 0 && auto.right <= width)
                assertTrue(label("Stopped").boundsInRoot.width > 0, "Capture status must retain intrinsic width in the scroll lane")
                val scroll = nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                scroll.config[SemanticsActions.ScrollBy].action!!.invoke(10000f, 0f)
                repeat(10) { frame() }
                assertEquals(auto, label("Auto scroll").boundsInRoot)
                assertTrue(label("Export (3)").boundsInRoot.right < label("Conditions: Off").boundsInRoot.left)
                click("Import"); click("Export (3)")
                assertTrue(imported); assertTrue(exported)
            }
        }
    }
    @Test fun `capture and clear keep full height on initial narrow windows and resizing`() = EventQueue.invokeAndWait {
        val ip = "192.168.100.249"
        val port = 8181
        val endpoints = ProxyEndpointSnapshot(ProxyEndpointVersion(1), listOf(
            ProxyEndpoint("127.0.0.1", port, ProxyEndpointScope.LOOPBACK, ProxyAccessRequirement.LOCAL_PROCESS)
        ))
        val state = TrafficToolbarState(captureState = CaptureState.PAUSED,
            engineState = ProxyRuntimeState.Running(ProxyRuntimeHandle("toolbar-test", endpoints)),
            localIpAddress = ip, exportAvailable = true, selectedCount = 3)
        val forwarding = "Forwarding · Capture paused ($ip:$port)"
        for (density in listOf(Density(1f), Density(1.25f, 1.2f), Density(2f))) {
            val width = mutableStateOf(900.dp)
            ImageComposeScene((1800 * density.density).toInt(), (100 * density.density).toInt(), density,
                coroutineContext = UnconfinedTestDispatcher(), content = { KNetTheme {
                Column(Modifier.width(width.value)) {
                    TrafficToolbar(state,
                        TrafficToolbarActions(onImport = {}, onExport = {}),
                        networkConditionsControl = { KNetButton({}) {
                            Text("Conditions: Streaming 100 kbps", maxLines = 1, softWrap = false)
                        } })
                }
            } }).use { scene ->
                var time = 0L
                fun frames() { repeat(10) { scene.render(time.also { time += 250_000_000 }).close() } }
                fun nodes(): List<SemanticsNode> {
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    return scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                }
                fun label(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }
                fun assertForwardingFits() {
                    val status = label(forwarding)
                    val layouts = mutableListOf<TextLayoutResult>()
                    assertTrue(status.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
                    val layout = layouts.single()
                    assertEquals(1, layout.lineCount)
                    assertFalse(layout.isLineEllipsized(0) || layout.multiParagraph.maxIntrinsicWidth > layout.size.width + 1f,
                        "Forwarding endpoint is truncated at ${width.value} and $density " +
                            "(rendered width ${layout.size.width}, intrinsic width ${layout.multiParagraph.maxIntrinsicWidth})")
                    assertEquals(layout.size.width.toFloat(), status.boundsInRoot.width, 1f, "Forwarding endpoint is clipped by the toolbar")
                }
                frames()
                val labels = listOf("Start Capture", "Pause Capture", "Clear")
                val initialHeights = labels.associateWith { label(it).boundsInRoot.height }
                val initialTop = label("Auto scroll").boundsInRoot.top
                assertTrue(nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                    .config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0)
                if (density.density == 2f) scene.render(time).use { image -> image.encodeToData()!!.use { File("/tmp/knet-traffic-toolbar-narrow.png").writeBytes(it.bytes) } }
                width.value = 1800.dp; frames()
                assertForwardingFits()
                labels.forEach { assertEquals(label(it).boundsInRoot.height, initialHeights.getValue(it), "Initial narrow $it was clipped") }
                width.value = 900.dp; frames()
                labels.forEach { assertEquals(initialHeights.getValue(it), label(it).boundsInRoot.height) }
                assertEquals(initialTop, label("Auto scroll").boundsInRoot.top)
                val scroll = nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                val scrollBy = scroll.config[SemanticsActions.ScrollBy].action!!
                val status = label(forwarding)
                val hiddenWidth = status.positionInRoot.x + status.size.width - status.boundsInRoot.right
                assertTrue(scrollBy(hiddenWidth.coerceAtLeast(0f), 0f))
                frames()
                assertForwardingFits()
                assertTrue(scrollBy(10_000f, 0f)); frames()
                assertTrue(label("Export (3)").boundsInRoot.height >= 12 * density.density)
                assertTrue(label("Export (3)").boundsInRoot.right < label("Auto scroll").boundsInRoot.left)
            }
        }
    }

}
