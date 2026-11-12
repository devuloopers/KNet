package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Header measurement keeps page geometry stable and controls accessible across window and text sizes. */
@OptIn(ExperimentalComposeUiApi::class)
class KNetScreenLayoutTest {
    @Test
    fun `page titles subtitles and content align with differently sized actions`() = EventQueue.invokeAndWait {
        for (scale in listOf(1f, 1.5f)) {
            var contentTop: Float? = null
            for ((index, title) in listOf("Intercepts", "Network Conditions", "Protected Traffic").withIndex()) {
                fixture(1280, scale, title, actions = {
                    Box(Modifier.width(260.dp).height((24 + index * 10).dp).testTag("actions"))
                }) { scene ->
                    val heading = scene.text(title)
                    assertNotNull(heading.config.getOrNull(SemanticsProperties.Heading))
                    assertEquals(24f, heading.boundsInRoot.left)
                    assertEquals(16f, heading.boundsInRoot.top)
                    assertEquals(20.sp, heading.textLayout().layoutInput.style.fontSize)
                    assertEquals(12.sp, scene.text(SUBTITLE).textLayout().layoutInput.style.fontSize)
                    val currentTop = scene.tag("content").boundsInRoot.top
                    if (contentTop != null) assertEquals(contentTop, currentTop)
                    contentTop = currentTop
                    assertTrue(scene.tag("actions").boundsInRoot.right <= 1280 - 24)
                    scene.save("aligned-$index-$scale")
                }
            }
        }
    }

    @Test
    fun `narrow larger text headers stack and flow controls without covering the heading`() = EventQueue.invokeAndWait {
        fixture(360, 1.5f, "Network Conditions", actions = {
            Box(Modifier.width(200.dp).height(30.dp).testTag("first action"))
            Box(Modifier.width(200.dp).height(30.dp).testTag("second action"))
        }) { scene ->
            val heading = scene.text("Network Conditions")
            val subtitle = scene.text(SUBTITLE)
            val first = scene.tag("first action").boundsInRoot
            val second = scene.tag("second action").boundsInRoot
            assertEquals(1, heading.textLayout().lineCount)
            assertEquals(1, subtitle.textLayout().lineCount)
            assertTrue(first.top > subtitle.boundsInRoot.bottom)
            assertTrue(second.top > first.bottom)
            assertTrue(second.bottom < scene.tag("content").boundsInRoot.top)
            for (bounds in listOf(first, second)) {
                assertTrue(bounds.left >= 24 && bounds.right <= 360 - 24)
            }
            scene.save("narrow-larger-text")
        }
    }

    @Test
    fun `tall or absent controls do not shift the page title`() = EventQueue.invokeAndWait {
        fixture(1280, 1f, "Protected Traffic", actions = {
            Box(Modifier.width(260.dp).height(100.dp).testTag("actions"))
        }) { scene ->
            assertEquals(16f, scene.text("Protected Traffic").boundsInRoot.top)
            assertTrue(scene.tag("content").boundsInRoot.top > scene.tag("actions").boundsInRoot.bottom)
        }
        fixture(1280, 1f, "Intercepts") { scene ->
            assertEquals(16f, scene.text("Intercepts").boundsInRoot.top)
            assertEquals(80f, scene.tag("content").boundsInRoot.top)
        }
    }

    @Test
    fun `short pages reduce content spacing while retaining title and subtitle`() = EventQueue.invokeAndWait {
        fixture(1280, 1f, "Intercepts", height = 560) { scene ->
            assertEquals(16f, scene.text("Intercepts").boundsInRoot.top)
            assertEquals(1, scene.text(SUBTITLE).textLayout().lineCount)
            assertEquals(72f, scene.tag("content").boundsInRoot.top)
            assertTrue(scene.tag("content").boundsInRoot.height > 560 / 2f)
        }
    }

    private fun fixture(width: Int, scale: Float, title: String, actions: (@Composable () -> Unit)? = null,
        height: Int = 800,
        check: (ImageComposeScene) -> Unit) {
        ImageComposeScene(width, height, Density(1f, scale), content = { KNetTheme {
            KNetScreenLayout(title, SUBTITLE, actions = actions) {
                Box(Modifier.weight(1f).fillMaxWidth().testTag("content"))
            }
        }}).use { scene ->
            repeat(4) { scene.render((it + 1) * 1_000_000_000L).close() }
            check(scene)
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(assertNotNull(config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(results))
        return results.single()
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        descendants(owner.rootSemanticsNode)
    }
    private fun ImageComposeScene.text(text: String) = nodes().first {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == text }
    }
    private fun ImageComposeScene.tag(tag: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    private fun ImageComposeScene.save(name: String) {
        render(6_000_000_000L).use { image -> image.encodeToData()?.use {
            File("/tmp/knet-screen-header-$name.png").writeBytes(it.bytes)
        }}
    }

    private companion object {
        const val SUBTITLE = "Manage existing controls and saved connection rules."
    }
}
