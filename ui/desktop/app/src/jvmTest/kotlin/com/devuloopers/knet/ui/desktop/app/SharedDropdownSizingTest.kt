package com.devuloopers.knet.ui.desktop.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownDefaults
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.*

/** Desktop composition verifies the reusable dropdown contract without feature-specific sizing options. */
@OptIn(ExperimentalComposeUiApi::class)
class SharedDropdownSizingTest {
    private var frameNanos = 0L

    @Test
    fun `shared defaults fit the selected label while explicit form widths remain authoritative`() = EventQueue.invokeAndWait {
        val selected = mutableStateOf("Off")
        val options = mutableStateOf(listOf("Off", "Streaming 100 kbps"))
        ImageComposeScene(1000, 500, content = {
            KNetTheme(motion = Motion(animationsEnabled = false)) {
                Column(Modifier.fillMaxSize()) {
                    KNetDropdown(selected.value, options.value, { selected.value = it }, Modifier.testTag("automatic"))
                    KNetDropdown(selected.value, options.value, { selected.value = it }, Modifier.width(240.dp).testTag("fixed"))
                    Box(Modifier.width(320.dp)) {
                        KNetDropdown(selected.value, options.value, { selected.value = it }, Modifier.fillMaxWidth().testTag("form"))
                    }
                }
            }
        }).use { scene ->
            scene.frame()
            val short = scene.width("automatic")
            val fixed = scene.width("fixed")
            val form = scene.width("form")
            val veryLong = "A long custom selection that exceeds the shared dropdown anchor width limit"
            options.value += veryLong
            scene.frame()
            assertEquals(short, scene.width("automatic"), "An unused long option must not reserve anchor space")
            selected.value = "Streaming 100 kbps"
            scene.frame()
            assertTrue(scene.width("automatic") > short)
            assertEquals(fixed, scene.width("fixed"))
            assertEquals(form, scene.width("form"))
            selected.value = veryLong
            scene.frame()
            assertEquals(KNetDropdownDefaults.MaximumWidth.value, scene.width("automatic"))
            assertEquals(fixed, scene.width("fixed"))
            assertEquals(form, scene.width("form"))
            selected.value = "Off"
            scene.frame()
            assertEquals(short, scene.width("automatic"))
        }
    }

    @Test
    fun `shared selected width remeasures when text scaling changes`() = EventQueue.invokeAndWait {
        val fontScale = mutableStateOf(1f)
        val selected = "Streaming 100 kbps"
        ImageComposeScene(1000, 500, content = {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                KNetTheme(motion = Motion(animationsEnabled = false)) {
                    Box(Modifier.fillMaxSize()) {
                        KNetDropdown(selected, listOf("Off", selected), {}, Modifier.testTag("automatic"))
                    }
                }
            }
        }).use { scene ->
            scene.frame()
            val normal = scene.width("automatic")
            fontScale.value = 1.5f
            scene.frame()
            assertTrue(scene.width("automatic") > normal)
            val text = scene.nodes().first {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == selected } &&
                    it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null
            }
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(text.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(layouts))
            assertTrue(layouts.isNotEmpty())
            assertFalse(layouts.any { it.isLineEllipsized(0) || it.multiParagraph.maxIntrinsicWidth > it.size.width })
            fontScale.value = 1f
            scene.frame()
            assertEquals(normal, scene.width("automatic"))
        }
    }

    private fun ImageComposeScene.frame() {
        repeat(4) { frameNanos += 1_000_000_000L; render(frameNanos).close() }
    }
    private fun ImageComposeScene.width(tag: String) = nodes().first {
        it.config.getOrNull(SemanticsProperties.TestTag) == tag
    }.boundsInRoot.width
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
}
