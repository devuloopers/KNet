package com.devuloopers.knet.ui.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.components.table.KNetTableViewport
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Actual rendered table header must remain continuous above the vertical scrollbar lane. */
@OptIn(ExperimentalComposeUiApi::class)
class SharedTableViewportTest {
    @Test fun `header background reaches the edge and row scrollbar starts below measured header`() = EventQueue.invokeAndWait {
        for (width in listOf(680, 1280)) for (scale in listOf(1f, 1.5f)) fixture(width, scale, 100) { scene, color ->
            val header = scene.tag("header").boundsInWindow
            val rows = scene.tag("rows").boundsInWindow
            val scrollbar = scene.nodes().single { "Scroll table rows" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertEquals(header.bottom, scrollbar.boundsInWindow.top, 0.1f, "Scrollbar must start after the actual header, including larger text")
            assertEquals(rows.bottom, scrollbar.boundsInWindow.bottom, 0.1f)
            scene.render(6_000_000_000L).use { image ->
                image.encodeToData().use { data ->
                    val bitmap = ImageIO.read(ByteArrayInputStream(assertNotNull(data).bytes))
                    assertEquals(color, bitmap.getRGB(width - 5, header.center.y.toInt()), "The scrollbar lane must not leave a gap in the header")
                }
            }
            val before = scene.tag("header-details").boundsInWindow.left
            assertEquals(before, scene.tag("row-details").boundsInWindow.left, 0.1f)
            if (width == 680) {
                val horizontal = scene.nodes().first { it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null }
                assertTrue(assertNotNull(horizontal.config.getOrNull(SemanticsActions.ScrollBy)?.action).invoke(120f, 0f))
                repeat(5) { scene.render((it + 7) * 1_000_000_000L).close() }
                val after = scene.tag("header-details").boundsInWindow.left
                assertTrue(after < before, "Header must move with horizontal scrolling")
                assertEquals(after, scene.tag("row-details").boundsInWindow.left, 0.1f)
            }
            val directory = File("build/reports/table-viewport-ui").apply { mkdirs() }
            scene.render(12_000_000_000L).use { image ->
                image.encodeToData()?.use { File(directory, "header-$width-$scale.png").writeBytes(it.bytes) }
            }
        }
    }

    @Test fun `short tables reserve no unused scrollbar gutter`() = EventQueue.invokeAndWait {
        fixture(1280, 1f, 2) { scene, _ ->
            assertEquals(1280f, scene.tag("header").boundsInWindow.width)
            assertEquals(1280f, scene.tag("rows").boundsInWindow.width)
            assertFalse(scene.nodes().any { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any {
                it == "Scroll table rows" || it == "Scroll table columns"
            } })
        }
    }

    private fun fixture(width: Int, scale: Float, count: Int, block: (ImageComposeScene, Int) -> Unit) {
        var headerColor = 0
        ImageComposeScene(width, 360, Density(1f, scale), content = {
            KNetTheme {
                headerColor = KNetTheme.colors.panelHeader.toArgb()
                val rows = rememberLazyListState()
                KNetTableViewport(rows, Modifier.fillMaxSize(), header = { modifier ->
                    Row(modifier.testTag("header").background(KNetTheme.colors.panelHeader).padding(KNetTheme.spacing.md)) {
                        Text("Name", Modifier.width(200.dp))
                        Text("Details", Modifier.weight(1f).testTag("header-details"))
                    }
                }) { modifier ->
                    LazyColumn(modifier.testTag("rows"), state = rows) {
                        items(count) { index ->
                            Row(Modifier.height(42.dp).fillMaxWidth().padding(horizontal = KNetTheme.spacing.md)) {
                                Text("Row $index", Modifier.width(200.dp))
                                Text("Value $index", Modifier.weight(1f).then(if (index == 0) Modifier.testTag("row-details") else Modifier))
                            }
                        }
                    }
                }
            }
        }).use { scene ->
            repeat(5) { scene.render((it + 1) * 1_000_000_000L).close() }
            block(scene, headerColor)
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    private fun ImageComposeScene.tag(tag: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
}
