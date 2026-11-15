package com.devuloopers.knet.ui.core.components.button

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.components.menu.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.*

/** Real split control and nested KNet popup input; callback data stays frozen and feedback never shifts bounds. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class KNetSplitCopyButtonTest {
    @Test fun `quick copy follows tab label nested menus invoke once and execution changes dismiss old popups`() = EventQueue.invokeAndWait {
        for (scale in listOf(1f, 2f)) {
            val label = mutableStateOf("Copy body"); val owner = mutableStateOf("A"); val copied = mutableStateOf(0L)
            val calls = mutableListOf<String>()
            var contentDecoration = 0f
            ImageComposeScene((900 * scale).toInt(), (550 * scale).toInt(), Density(scale), content = { KNetTheme {
                contentDecoration = (KNetTheme.spacing.md * 2 + KNetTheme.spacing.sm + KNetTheme.dimensions.iconSizeMedium).value * scale
                val captured = owner.value
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    KNetSplitCopyButton(label.value, listOf(ContextMenuSubmenu("Response", listOf(ContextMenuItem("Original text") { calls += "$captured-original" }))),
                        owner.value, true, false, copied.value, null, { calls += label.value; copied.value++ })
                }
            } }).use { scene ->
                var time = 0L
                fun frames() { repeat(7) { scene.render(time.also { time += 100_000_000 }).close() } }
                fun nodes(): List<SemanticsNode> {
                    fun visit(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::visit)
                    return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
                }
                fun description(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(value) }
                fun text(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value } }
                fun click(node: SemanticsNode) { val point = node.boundsInWindow.center
                    scene.sendPointerEvent(PointerEventType.Press, point, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
                    scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary); frames()
                }
                fun assertCompactLabel() {
                    // A button's merged text semantics cover the whole click target. Measure the actual
                    // label layout to exclude that hit area when checking its normal content padding.
                    val labelNode = nodes().first { node ->
                        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == label.value } &&
                            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action != null
                    }
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    assertTrue(assertNotNull(labelNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(layouts))
                    val labelWidth = layouts.single { it.layoutInput.text.text == label.value }.size.width
                    val expectedWidth = labelWidth + contentDecoration
                    val actualWidth = description(label.value).boundsInWindow.width
                    assertTrue(kotlin.math.abs(actualWidth - expectedWidth) <= 1f,
                        "The visible label should have only normal icon spacing and theme padding: $actualWidth / $expectedWidth")
                }
                frames(); val initial = description("Copy options").boundsInWindow
                assertCompactLabel()
                if (scale == 1f) scene.render(time.also { time += 100_000_000 }).use { rendered ->
                    rendered.encodeToData()!!.use { java.io.File("/tmp/knet-copy-spacing.png").writeBytes(it.bytes) }
                }
                click(description("Copy body")); assertEquals(listOf("Copy body"), calls)
                assertEquals(initial, description("Copy options").boundsInWindow)
                label.value = "Copy headers"; frames()
                assertCompactLabel()
                assertTrue(description("Copy options").boundsInWindow.left > initial.left,
                    "The longer visible label should grow the primary segment")
                label.value = "Copy cookies"; frames(); assertCompactLabel()
                label.value = "Copy body"; frames(); assertCompactLabel()
                assertEquals(initial, description("Copy options").boundsInWindow,
                    "Returning to Body must release the extra label width")
                click(description("Copy options")); click(text("Response")); click(text("Original text"))
                assertEquals(listOf("Copy body", "A-original"), calls)
                click(description("Copy options")); assertNotNull(text("Response")); owner.value = "B"; frames()
                assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == "Original text" || value.text == "Response" } })
            }
        }
    }
    @Test fun `anchored dropdown flips above and stays inside both layout directions`() {
        val provider = AnchoredActionMenuPosition(4)
        val bottom = IntRect(700, 450, 728, 478)
        val ltr = provider.calculatePosition(bottom, IntSize(800, 500), LayoutDirection.Ltr, IntSize(250, 300))
        assertEquals(IntOffset(478, 146), ltr)
        val rtl = provider.calculatePosition(bottom, IntSize(800, 500), LayoutDirection.Rtl, IntSize(250, 300))
        assertEquals(IntOffset(550, 146), rtl)
    }
}
