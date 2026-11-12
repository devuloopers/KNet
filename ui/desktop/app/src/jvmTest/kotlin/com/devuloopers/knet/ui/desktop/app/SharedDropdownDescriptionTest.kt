package com.devuloopers.knet.ui.desktop.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltipDefaults
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Exercises real hover, focus, selection and tooltip constraints without opening a desktop window. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class SharedDropdownDescriptionTest {
    private val options = listOf("Inspect", "Tunnel", "Block")
    private fun description(item: String) = "$item explanation. Saved rules take priority. Changes apply to new connections."

    @Test fun `focused anchor help disappears immediately on pointer exit and stays hidden until reentry`() = fixture { ui ->
        assertTrue(assertNotNull(ui.anchor().config.getOrNull(SemanticsActions.RequestFocus)?.action).invoke())
        ui.frame(); ui.hover(ui.anchor()); ui.advance(500)
        assertTrue(ui.helpShown("Inspect"))
        ui.exit()
        assertTrue(ui.anchor().config.getOrNull(SemanticsProperties.Focused) == true)
        assertFalse(ui.helpShown("Inspect"), "Retained focus must not keep a hover tooltip visible")
        ui.advance(5_000)
        assertFalse(ui.helpShown("Inspect"), "Focus must not reopen help while the pointer is outside")
        ui.hover(ui.anchor()); ui.advance(499)
        assertFalse(ui.helpShown("Inspect"))
        ui.advance(1); assertTrue(ui.helpShown("Inspect"))
        val popup = ui.nodes().first { it.text() == description("Inspect") }
        ui.hover(popup)
        assertFalse(ui.helpShown("Inspect"), "Moving from the control onto its popup must also dismiss help")
    }

    @Test fun `focused menu row loses hover help on exit without losing its keyboard selection`() = fixture { ui ->
        assertTrue(assertNotNull(ui.anchor().config.getOrNull(SemanticsActions.RequestFocus)?.action).invoke())
        ui.frame()
        ui.activate(ui.anchor())
        assertTrue(assertNotNull(ui.option("Tunnel").config.getOrNull(SemanticsActions.RequestFocus)?.action).invoke())
        ui.frame(); ui.hover(ui.option("Tunnel")); ui.advance(500)
        assertTrue(ui.helpShown("Tunnel"))
        ui.exit()
        assertFalse(ui.helpShown("Tunnel"), "A focused menu row must obey pointer exit")
        ui.advance(5_000)
        assertFalse(ui.helpShown("Tunnel"))
        assertTrue(ui.option("Tunnel").config.getOrNull(SemanticsProperties.Focused) == true)
        ui.key(Key.Enter)
        assertEquals("Tunnel", ui.selected.value)
    }

    @Test fun `selected help is delayed and cancels on exit disable description change and disposal`() = fixture { ui ->
        val originalBounds = ui.anchor().boundsInWindow
        ui.hover(ui.anchor())
        ui.advance(499)
        assertFalse(ui.helpShown("Inspect"))
        ui.advance(1)
        assertTrue(ui.helpShown("Inspect"))
        assertEquals(originalBounds, ui.anchor().boundsInWindow)
        ui.exit()
        assertFalse(ui.helpShown("Inspect"))
        ui.hover(ui.anchor()); ui.advance(200); ui.exit(); ui.advance(600)
        assertFalse(ui.helpShown("Inspect"), "Leaving before the delay must cancel help")
        ui.hover(ui.anchor()); ui.advance(500)
        ui.selected.value = "Tunnel"; ui.frame()
        assertFalse(ui.helpShown("Inspect")); assertFalse(ui.helpShown("Tunnel"))
        ui.advance(500); assertTrue(ui.helpShown("Tunnel"))
        ui.enabled.value = false; ui.frame(); ui.advance(600)
        assertFalse(ui.helpShown("Tunnel"))
        ui.enabled.value = true; ui.frame(); ui.exit(); ui.hover(ui.anchor()); ui.advance(500)
        assertTrue(ui.helpShown("Tunnel"))
        ui.described.value = false; ui.frame(); ui.advance(600)
        assertFalse(ui.helpShown("Tunnel"))
        ui.described.value = true; ui.frame(); ui.exit(); ui.hover(ui.anchor()); ui.advance(500)
        assertTrue(ui.helpShown("Tunnel"))
        ui.mounted.value = false; ui.frame(); ui.advance(600)
        assertFalse(ui.helpShown("Tunnel"))
    }

    @Test fun `each menu option explains on hover without selecting or changing geometry and remains clickable`() = fixture { ui ->
        ui.activate(ui.anchor()); ui.exit()
        for (option in options) {
            val row = ui.option(option)
            val bounds = row.boundsInWindow
            assertTrue(description(option) in row.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
            ui.hover(row); ui.advance(KNetTooltipDefaults.ShowDelayMillis)
            assertTrue(ui.helpShown(option), "Missing menu help for $option")
            assertEquals("Inspect", ui.selected.value, "Hover must not change the policy")
            assertEquals(bounds, ui.option(option).boundsInWindow)
        }
        ui.click(ui.option("Block"))
        assertEquals("Block", ui.selected.value)
        assertFalse(ui.helpShown("Block"), "Selection must dismiss the menu explanation")
    }

    @Test fun `keyboard focus keeps descriptions accessible without opening hover help or changing selection`() = fixture { ui ->
        val anchor = ui.anchor()
        assertTrue(assertNotNull(anchor.config.getOrNull(SemanticsActions.RequestFocus)?.action).invoke())
        ui.frame(); ui.advance(500)
        assertFalse(ui.helpShown("Inspect"), "Keyboard focus alone must never open hover help")
        assertTrue(description("Inspect") in ui.anchor().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
        assertTrue(ui.anchor().config.getOrNull(SemanticsProperties.Focused) == true)
        ui.key(Key.Escape); ui.advance(600)
        assertFalse(ui.helpShown("Inspect"))
        assertTrue(ui.anchor().config.getOrNull(SemanticsProperties.Focused) == true)
        ui.key(Key.Enter)
        assertFalse(ui.helpShown("Inspect"))
        ui.key(Key.DirectionDown)
        assertTrue(ui.option("Tunnel").config.getOrNull(SemanticsProperties.Focused) == true)
        ui.key(Key.DirectionDown)
        assertTrue(ui.option("Block").config.getOrNull(SemanticsProperties.Focused) == true)
        ui.key(Key.DirectionDown)
        assertTrue(ui.option("Inspect").config.getOrNull(SemanticsProperties.Focused) == true)
        ui.key(Key.DirectionUp); ui.key(Key.DirectionUp); ui.advance(500)
        assertFalse(ui.helpShown("Tunnel"))
        assertTrue(description("Tunnel") in ui.option("Tunnel").config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
        assertTrue(ui.option("Tunnel").config.getOrNull(SemanticsProperties.Focused) == true)
        assertEquals("Inspect", ui.selected.value)
        ui.key(Key.Enter)
        assertEquals("Tunnel", ui.selected.value)
        ui.activate(ui.anchor()); ui.key(Key.DirectionDown); ui.key(Key.Spacebar)
        assertEquals("Block", ui.selected.value)
    }

    @Test fun `help wraps inside a narrow larger text window and preserves explicit anchor size`() = fixture(360, 560, 1.5f) { ui ->
        val before = ui.anchor().boundsInWindow
        ui.hover(ui.anchor()); ui.advance(500)
        val help = ui.nodes().first { it.text() == description("Inspect") }
        assertTrue(help.boundsInWindow.left >= 0 && help.boundsInWindow.right <= 360)
        assertTrue(help.boundsInWindow.top >= 0 && help.boundsInWindow.bottom <= 560)
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(assertNotNull(help.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(layouts))
        assertTrue(layouts.all { !it.hasVisualOverflow })
        assertEquals(before, ui.anchor().boundsInWindow)
        ui.save("dropdown-description-narrow")
    }

    @Test fun `narrow menu still allows selecting a different row while help is visible`() = fixture(360, 560, 1.5f) { ui ->
        ui.activate(ui.anchor()); ui.exit()
        ui.hover(ui.option("Inspect")); ui.advance(500)
        assertTrue(ui.helpShown("Inspect"))
        ui.click(ui.option("Block"))
        assertEquals("Block", ui.selected.value)
    }

    private fun fixture(width: Int = 1000, height: Int = 600, fontScale: Float = 1f, block: (Ui) -> Unit) = EventQueue.invokeAndWait {
        val ui = Ui()
        ImageComposeScene(width, height, Density(1f, fontScale), coroutineContext = UnconfinedTestDispatcher(ui.clock), content = {
            KNetTheme(motion = Motion(animationsEnabled = false)) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    if (ui.mounted.value) KNetDropdown(ui.selected.value, options, { ui.selected.value = it },
                        Modifier.width(260.dp).testTag("described"), enabled = ui.enabled.value,
                        itemDescription = if (ui.described.value) ::description else null)
                    else Text("Removed")
                }
            }
        }).use { scene -> ui.scene = scene; ui.frame(); block(ui) }
    }

    private inner class Ui {
        val clock = TestCoroutineScheduler()
        val selected = mutableStateOf("Inspect")
        val enabled = mutableStateOf(true)
        val described = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        lateinit var scene: ImageComposeScene
        private var time = 0L
        fun frame() { repeat(3) { time += 100_000_000L; scene.render(time).close(); clock.runCurrent() } }
        fun advance(milliseconds: Long) { clock.advanceTimeBy(milliseconds); clock.runCurrent(); frame() }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun anchor() = descendants(nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "described" })
            .first { it.config.getOrNull(SemanticsActions.OnClick) != null }
        fun option(text: String) = nodes().first { it.config.getOrNull(SemanticsActions.OnClick) != null &&
            it.config.getOrNull(SemanticsProperties.Selected) != null && descendants(it).any { child -> child.text() == text } }
        fun helpShown(item: String) = nodes().any { it.text() == description(item) }
        fun hover(node: SemanticsNode) { scene.sendPointerEvent(PointerEventType.Move, node.boundsInWindow.center); frame() }
        fun exit() { scene.sendPointerEvent(PointerEventType.Move, Offset(2f, 550f)); frame() }
        fun activate(node: SemanticsNode) { assertTrue(assertNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action).invoke()); frame() }
        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frame()
        }
        fun click(node: SemanticsNode) {
            val position = node.boundsInWindow.center
            scene.sendPointerEvent(PointerEventType.Move, position)
            scene.sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, position, buttons = PointerButtons(), button = PointerButton.Primary)
            frame()
        }
        fun save(name: String) {
            val directory = File("build/reports/protected-traffic-ui").apply { mkdirs() }
            scene.render(++time).use { it.encodeToData()?.use { data -> File(directory, "$name.png").writeBytes(data.bytes) } }
        }
    }
}

private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
