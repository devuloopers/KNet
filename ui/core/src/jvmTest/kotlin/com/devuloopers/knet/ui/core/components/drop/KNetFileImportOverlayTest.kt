package com.devuloopers.knet.ui.core.components.drop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.*

/** The real shared surface retains its bounds and identity as import contents change. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class KNetFileImportOverlayTest {
    @Test fun `theme bounds remain fixed across content states window sizes and densities`() = EventQueue.invokeAndWait {
        for (density in listOf(1f, 2f)) for ((width, height) in listOf(380 to 500, 680 to 900, 1200 to 950)) {
            val expanded = mutableStateOf(false)
            var expectedWidth = 0f; var expectedHeight = 0f
            ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density), content = { KNetTheme {
                val margin = KNetTheme.spacing.lg.value * 2
                expectedWidth = minOf(width - margin, KNetTheme.dimensions.dialogWidthLarge.value) * density
                expectedHeight = minOf(height - margin, KNetTheme.dimensions.dialogContentMaxHeight.value + margin) * density
                KNetFileImportOverlay({}) { Box(androidx.compose.ui.Modifier.height(if (expanded.value) 600.dp else 10.dp)) }
            } }).use { scene ->
                fun pane(): SemanticsNode {
                    fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
                    return scene.semanticsOwners.flatMap { descend(it.rootSemanticsNode) }.single { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Import capture" }
                }
                repeat(6) { scene.render(it * 100_000_000L).close() }
                val initial = pane(); val bounds = initial.boundsInRoot; val id = initial.id
                assertEquals(expectedWidth, bounds.width, 1f); assertEquals(expectedHeight, bounds.height, 1f)
                expanded.value = true
                repeat(6) { scene.render((it + 6) * 100_000_000L).close() }
                assertEquals(id, pane().id); assertEquals(bounds, pane().boundsInRoot)
            }
        }
    }
}
