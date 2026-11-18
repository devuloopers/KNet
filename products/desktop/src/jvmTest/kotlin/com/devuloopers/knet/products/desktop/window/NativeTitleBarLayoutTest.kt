package com.devuloopers.knet.products.desktop.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import java.awt.EventQueue
import java.lang.reflect.Proxy
import com.jetbrains.WindowDecorations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Native geometry follows measured layout and never cycles merely because the shell recomposes. */
@OptIn(ExperimentalComposeUiApi::class)
class NativeTitleBarLayoutTest {
    @Test
    fun `native proxy equality cannot repeatedly dispose the decoration on recomposition`() {
        // Reproduce JBR 21's generated API proxy semantics without requiring JBR in the test JVM.
        val api = Proxy.newProxyInstance(WindowDecorations.CustomTitleBar::class.java.classLoader,
            arrayOf(WindowDecorations.CustomTitleBar::class.java)) { _, method, _ ->
            when (method.name) {
                "equals" -> false
                "hashCode" -> 1
                else -> null
            }
        } as WindowDecorations.CustomTitleBar
        assertFalse(api.equals(api))
        EventQueue.invokeAndWait {
            val revision = mutableIntStateOf(0)
            var mounts = 0
            var disposals = 0
            ImageComposeScene(400, 80, content = {
                val generation = revision.intValue
                val handle = remember { NativeTitleBarHandle(api) }
                DisposableEffect(handle) { mounts++; onDispose { disposals++ } }
                Box(Modifier.width((200 + generation).dp).height(40.dp))
            }).use { scene ->
                scene.render(1_000_000)
                repeat(5) { revision.intValue++; scene.render((it + 2) * 1_000_000L) }
                assertEquals(1, mounts, "The native title bar must stay mounted while the shell updates")
                assertEquals(0, disposals, "No restore-to-system-caption operation is allowed during recomposition")
            }
            assertEquals(1, disposals, "The native title bar is released once when the window closes")
        }
    }

    @Test
    fun `shell recomposition does not reapply native geometry and DPI retains logical height`() {
        EventQueue.invokeAndWait {
            for (scale in listOf(1f, 1.25f, 2f)) {
                val revision = mutableIntStateOf(0)
                val width = mutableIntStateOf(400)
                val applied = mutableListOf<Float>()
                ImageComposeScene(1200, 150, density = Density(scale), content = {
                    val generation = revision.intValue
                    NativeTitleBarScope(0.dp, 141.dp, {}, { height ->
                        check(generation >= 0)
                        applied += height
                    }).TitleBar { Box(Modifier.width(width.intValue.dp).height(40.dp)) }
                }).use { scene ->
                    scene.render(1_000_000)
                    assertEquals(listOf(40f), applied)
                    revision.intValue++
                    scene.render(2_000_000)
                    scene.render(3_000_000)
                    assertEquals(listOf(40f), applied, "Unchanged layout must not repaint native decoration")
                    width.intValue = 450
                    scene.render(4_000_000)
                    assertEquals(listOf(40f, 40f), applied, "A real resize must reposition native controls")
                }
            }
        }
    }
}
