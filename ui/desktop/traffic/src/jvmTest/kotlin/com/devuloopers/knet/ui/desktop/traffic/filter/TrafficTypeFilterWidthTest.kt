package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficTypeFilter
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Actual filter interactions exercise label-sized anchors, readable menus and both animation directions. */
@OptIn(ExperimentalComposeUiApi::class)
class TrafficTypeFilterWidthTest {
    private var frameNanos = 0L

    @Test
    fun `type grows and contracts with selection while menu labels remain readable`() = EventQueue.invokeAndWait {
        val state = mutableStateOf(TrafficFilterBarState())
        var selections = 0
        ImageComposeScene(1440, 600, content = {
            KNetTheme {
                Box(Modifier.fillMaxSize()) {
                    TrafficFilterBar(state.value, TrafficFilterBarActions(onTypeSelected = { selected ->
                        selections++
                        state.value = state.value.copy(selectedType = selected)
                    }))
                }
            }
        }).use { scene ->
            scene.frame()
            val initial = scene.anchor().boundsInRoot
            scene.click(scene.anchor()); scene.frame()
            scene.save("type-menu")
            TrafficTypeFilter.entries.forEach { option -> scene.assertLabelFits(option.label) }
            val option = scene.clickableText(TrafficTypeFilter.OPAQUE_TCP.label)
            assertTrue(option.boundsInRoot.width > initial.width, "Menu width must not be limited to the short placeholder")
            scene.click(option)
            scene.advanceBy(1); scene.advanceBy(1); scene.advanceBy(40)
            val expanding = scene.anchor().boundsInRoot
            scene.frame()
            val expanded = scene.anchor().boundsInRoot
            assertTrue(expanded.width > initial.width)
            assertTrue(expanding.width > initial.width && expanding.width < expanded.width,
                "Growth must pass through an intermediate width: $initial / $expanding / $expanded")
            assertEquals(initial.height, expanded.height)
            assertEquals(1, selections)
            scene.assertLabelFits(TrafficTypeFilter.OPAQUE_TCP.label)
            scene.save("type-selected")

            scene.click(scene.anchor()); scene.frame()
            scene.click(scene.clickableText(TrafficTypeFilter.ALL.label))
            scene.advanceBy(1); scene.advanceBy(1); scene.advanceBy(40)
            val contracting = scene.anchor().boundsInRoot
            scene.frame()
            val restored = scene.anchor().boundsInRoot
            assertTrue(contracting.width < expanded.width && contracting.width > restored.width,
                "Contraction must pass through an intermediate width")
            assertEquals(initial.width, restored.width)
            assertEquals(2, selections)
            scene.assertLabelFits("Type")
        }
    }

    @Test
    fun `larger text and reduced motion resize immediately without clipping`() = EventQueue.invokeAndWait {
        val state = mutableStateOf(TrafficFilterBarState())
        ImageComposeScene(1440, 600, Density(1f, 1.25f), content = {
            KNetTheme(motion = Motion(animationsEnabled = false)) {
                Box(Modifier.fillMaxSize()) { TrafficFilterBar(state.value, TrafficFilterBarActions()) }
            }
        }).use { scene ->
            scene.frame()
            val initial = scene.anchor().boundsInRoot.width
            state.value = state.value.copy(selectedType = TrafficTypeFilter.OPAQUE_TCP)
            scene.advanceBy(1); scene.advanceBy(1)
            val immediate = scene.anchor().boundsInRoot.width
            scene.frame()
            assertTrue(immediate > initial)
            assertEquals(immediate, scene.anchor().boundsInRoot.width)
            scene.assertLabelFits(TrafficTypeFilter.OPAQUE_TCP.label)
            scene.save("type-large-text")
            state.value = state.value.copy(selectedType = TrafficTypeFilter.ALL)
            scene.advanceBy(1); scene.advanceBy(1)
            assertEquals(initial, scene.anchor().boundsInRoot.width)
        }
    }

    private fun ImageComposeScene.anchor() = nodes().first {
        it.config.getOrNull(SemanticsProperties.TestTag) == "Traffic type filter"
    }

    private fun ImageComposeScene.clickableText(text: String) = nodes().first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && descendants(node).any { it.text() == text }
    }

    private fun ImageComposeScene.assertLabelFits(text: String) {
        val node = nodes().first { it.text() == text && it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(layouts))
        assertTrue(layouts.isNotEmpty())
        // Wrap-content Text can retain a paragraph wider than its rendered content. Check the actual text width.
        assertFalse(layouts.any { it.isLineEllipsized(0) || it.multiParagraph.maxIntrinsicWidth > it.size.width },
            "Clipped dropdown label: $text")
    }

    private fun ImageComposeScene.click(node: SemanticsNode) {
        val position = node.boundsInWindow.center
        sendPointerEvent(PointerEventType.Move, position)
        sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, buttons = PointerButtons(), button = PointerButton.Primary)
    }

    private fun ImageComposeScene.frame() { repeat(4) { advanceBy(1000) } }
    private fun ImageComposeScene.advanceBy(milliseconds: Long) {
        frameNanos += milliseconds * 1_000_000L
        render(frameNanos).close()
    }
    private fun ImageComposeScene.save(name: String) {
        val directory = File("build/reports/traffic-filter-ui").apply { mkdirs() }
        render(++frameNanos).use { picture -> picture.encodeToData()?.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}
