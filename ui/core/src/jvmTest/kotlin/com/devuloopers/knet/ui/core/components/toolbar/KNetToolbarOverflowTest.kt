package com.devuloopers.knet.ui.core.components.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.*

/** Horizontal overflow has its own row and cannot consume the fixed control-height budget. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class KNetToolbarOverflowTest {
    @Test fun `initial narrow layout and repeated resize retain full controls at both densities`() = EventQueue.invokeAndWait {
        for (density in listOf(1f, 2f)) for (initialWidth in listOf(480, 1200)) {
            val width = mutableStateOf(initialWidth.dp)
            var mainHeight = 0f; var gutter = 0f; var buttonHeight = 0f
            ImageComposeScene((1300 * density).toInt(), (120 * density).toInt(), Density(density), content = { KNetTheme {
                mainHeight = KNetTheme.dimensions.toolbarHeight.value * density
                gutter = (KNetTheme.dimensions.scrollbarWidth + KNetTheme.spacing.sm).value * density
                buttonHeight = KNetTheme.dimensions.buttonHeightStandard.value * density
                Column(Modifier.width(width.value)) {
                    KNetToolbar(scrollLeading = true, leading = {
                        KNetButton({}, Modifier.width(120.dp).testTag("capture")) { Text("Capture", maxLines = 1, softWrap = false) }
                        KNetButton({}, Modifier.width(80.dp).testTag("clear")) { Text("Clear", maxLines = 1, softWrap = false) }
                        KNetButton({}, Modifier.width(160.dp).testTag("export")) { Text("Export", maxLines = 1, softWrap = false) }
                    }, trailing = {
                        KNetButton({}, Modifier.width(200.dp).testTag("trailing")) { Text("Auto scroll", maxLines = 1, softWrap = false) }
                    })
                    Box(Modifier.fillMaxWidth().height(1.dp).testTag("below"))
                }
            } }).use { scene ->
                var time = 0L
                fun frames() { repeat(8) { scene.render(time.also { time += 250_000_000 }).close() } }
                fun nodes(): List<SemanticsNode> {
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    return scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                }
                fun tag(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == value }
                for (nextWidth in listOf(initialWidth, 1200, 480, 1200, 480)) {
                    width.value = nextWidth.dp
                    frames()
                    val scroll = nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                    val range = scroll.config[SemanticsProperties.HorizontalScrollAxisRange]
                    assertEquals(nextWidth == 480, range.maxValue() > 0)
                    // Reset horizontal position before comparing complete leading button bounds.
                    scroll.config[SemanticsActions.ScrollBy].action!!.invoke(-10_000f, 0f); frames()
                    for (value in listOf("capture", "clear", "trailing")) {
                        assertEquals(buttonHeight, tag(value).boundsInRoot.height, "Clipped $value at $nextWidth / density $density")
                        assertEquals((mainHeight - buttonHeight) / 2, tag(value).boundsInRoot.top)
                    }
                    assertEquals(mainHeight, scroll.boundsInRoot.height)
                    assertEquals(mainHeight + if (nextWidth == 480) gutter else 0f, tag("below").boundsInRoot.top)
                    scroll.config[SemanticsActions.ScrollBy].action!!.invoke(10_000f, 0f); frames()
                    assertEquals(buttonHeight, tag("export").boundsInRoot.height)
                    assertTrue(tag("export").boundsInRoot.right <= tag("trailing").boundsInRoot.left)
                }
            }
        }
    }

    @Test fun `toolbar without leading overflow keeps its original fixed height`() = EventQueue.invokeAndWait {
        var expected = 0f
        ImageComposeScene(900, 120, content = { KNetTheme {
            expected = KNetTheme.dimensions.toolbarHeight.value
            Column {
                KNetToolbar(leading = { Text("Capture", maxLines = 1, softWrap = false) },
                    trailing = { Text("Auto scroll", maxLines = 1, softWrap = false) })
                Box(Modifier.fillMaxWidth().height(1.dp).testTag("below"))
            }
        } }).use { scene ->
            repeat(6) { scene.render(it * 250_000_000L).close() }
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            val below = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                .first { it.config.getOrNull(SemanticsProperties.TestTag) == "below" }
            assertEquals(expected, below.boundsInRoot.top)
        }
    }
}
