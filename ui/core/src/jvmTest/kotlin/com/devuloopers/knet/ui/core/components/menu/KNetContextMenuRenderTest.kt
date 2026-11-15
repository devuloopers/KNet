package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Actual popup input verifies hover/click submenus, frozen actions, dismissal and keyboard ownership. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class KNetContextMenuRenderTest {
    @Test fun `secondary click prepares caller selection once before displaying its menu`() = EventQueue.invokeAndWait {
        var openings = 0
        fixture(onOpen = { openings++ }) { scene, _, _, frames ->
            scene.contextClick(); frames()
            assertEquals(1, openings, "Secondary release must not prepare or reopen the menu again")
            assertTrue("Protected Traffic" in scene.labels())
        }
    }

    @Test fun `hover opens submenu and choosing a leaf dismisses the complete menu once`() = EventQueue.invokeAndWait {
        fixture { scene, commands, _, frames ->
            scene.contextClick(); frames()
            scene.sendPointerEvent(PointerEventType.Move, scene.label("Protected Traffic").boundsInWindow.center); frames()
            assertNotNull(scene.label("Tunnel"))
            scene.click("Tunnel"); frames()
            assertEquals(listOf("tunnel"), commands)
            assertTrue(scene.labels().none { it == "Protected Traffic" || it == "Tunnel" })
        }
    }

    @Test fun `click opening keyboard navigation disabled actions and escape retain correct popup ancestry`() = EventQueue.invokeAndWait {
        fixture { scene, commands, _, frames ->
            scene.contextClick(); frames(); scene.click("Protected Traffic"); frames()
            assertNotNull(scene.label("Tunnel"))
            scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown)); frames()
            scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown)); frames()
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown)); frames()
            assertEquals(listOf("inspect"), commands)
            scene.contextClick(); frames()
            scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown)); frames()
            scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown)); frames()
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)); frames()
            assertTrue("Protected Traffic" in scene.labels()); assertFalse("Tunnel" in scene.labels())
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)); frames()
            assertFalse("Protected Traffic" in scene.labels())
        }
    }

    @Test fun `live target updates cannot replace commands in an already open menu and outside click dismisses`() = EventQueue.invokeAndWait {
        fixture { scene, commands, revision, frames ->
            scene.contextClick(); frames(); revision.value = 2; frames()
            scene.click("Copy"); frames(); assertEquals(listOf("copy-1"), commands)
            scene.contextClick(); frames()
            scene.sendPointerEvent(PointerEventType.Press, Offset(850f, 450f), buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, Offset(850f, 450f), button = PointerButton.Primary); frames()
            assertFalse("Protected Traffic" in scene.labels())
            scene.contextClick(); frames(); scene.click("Copy"); frames(); assertEquals(listOf("copy-1", "copy-2"), commands)
        }
    }

    private fun fixture(onOpen: () -> Unit = {}, check: (ImageComposeScene, MutableList<String>, androidx.compose.runtime.MutableState<Int>, () -> Unit) -> Unit) {
        val commands = mutableListOf<String>(); val revision = mutableStateOf(1)
        ImageComposeScene(900, 500, content = { KNetTheme {
            val version = revision.value
            KNetContextMenuArea(listOf(ContextMenuSubmenu("Protected Traffic", listOf(
                ContextMenuItem("Tunnel") { commands += "tunnel" }, ContextMenuItem("Inspect") { commands += "inspect" },
                ContextMenuItem("Block", enabled = false) { commands += "block" })), ContextMenuSeparator,
                ContextMenuItem("Copy") { commands += "copy-$version" }), onOpen = onOpen) {
                Box(Modifier.fillMaxSize()) { Text("Target", Modifier.padding(20.dp), color = KNetTheme.colors.textPrimary) }
            }
        } }).use { scene ->
            var time = 0L
            val frames = { repeat(6) { scene.render(time.also { time += 100_000_000 }).close() } }
            frames(); check(scene, commands, revision, frames)
        }
    }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        return semanticsOwners.flatMap { descend(it.rootSemanticsNode) }
    }
    private fun ImageComposeScene.labels() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
    private fun ImageComposeScene.label(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }
    private fun ImageComposeScene.contextClick() {
        sendPointerEvent(PointerEventType.Press, Offset(30f, 30f), buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
        sendPointerEvent(PointerEventType.Release, Offset(30f, 30f), button = PointerButton.Secondary)
    }
    private fun ImageComposeScene.click(value: String) {
        val position = label(value).boundsInWindow.center
        sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, button = PointerButton.Primary)
    }
}
