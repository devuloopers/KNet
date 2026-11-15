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
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.*

/** Narrow windows retain file/capture actions in the theme's scroll lane without moving trailing controls. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TrafficFileActionsRenderTest {
    @Test fun `import and selected export remain reachable with fixed auto scroll controls`() = EventQueue.invokeAndWait {
        for (width in listOf(900, 1440)) {
            var exported = false; var imported = false
            ImageComposeScene(width, 100, content = { KNetTheme {
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
        for (density in listOf(1f, 2f)) {
            val width = mutableStateOf(900.dp)
            ImageComposeScene((1800 * density).toInt(), (100 * density).toInt(), Density(density), content = { KNetTheme {
                Column(Modifier.width(width.value)) {
                    TrafficToolbar(TrafficToolbarState(exportAvailable = true, selectedCount = 3),
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
                frames()
                val labels = listOf("Start Capture", "Pause Capture", "Clear")
                val initialHeights = labels.associateWith { label(it).boundsInRoot.height }
                val initialTop = label("Auto scroll").boundsInRoot.top
                assertTrue(nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                    .config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0)
                if (density == 2f) scene.render(time).use { image -> image.encodeToData()!!.use { File("/tmp/knet-traffic-toolbar-narrow.png").writeBytes(it.bytes) } }
                width.value = 1800.dp; frames()
                labels.forEach { assertEquals(label(it).boundsInRoot.height, initialHeights.getValue(it), "Initial narrow $it was clipped") }
                width.value = 900.dp; frames()
                labels.forEach { assertEquals(initialHeights.getValue(it), label(it).boundsInRoot.height) }
                assertEquals(initialTop, label("Auto scroll").boundsInRoot.top)
                val scroll = nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                scroll.config[SemanticsActions.ScrollBy].action!!.invoke(10_000f, 0f); frames()
                assertTrue(label("Export (3)").boundsInRoot.height >= 12 * density)
                assertTrue(label("Export (3)").boundsInRoot.right < label("Auto scroll").boundsInRoot.left)
            }
        }
    }

}
