package com.devuloopers.knet.ui.core.components.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

/** Rendered geometry and real pointer/keyboard input protect the visible-line versus hit-target contract. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HorizontalSplitPaneTest {
    private var frame = 0L
    private var border = 0
    private var accent = 0
    private val ratio = mutableFloatStateOf(.5f)

    @Test fun `thin separator uses one logical pixel and eight logical pixel input target at both densities`() = EventQueue.invokeAndWait {
        for (density in listOf(1f, 2f)) {
            fixture(density = density) { scene ->
                val left = scene.tag("left").boundsInRoot
                val right = scene.tag("right").boundsInRoot
                assertEquals(density, right.left - left.right)
                assertEquals(8 * density, scene.handle().boundsInRoot.width)
                val image = scene.snapshot()
                assertEquals(density.toInt(), (left.right.toInt() - 4..right.left.toInt() + 4).count { image.getRGB(it, 100) == border })
                scene.save("thin-$density")
            }
        }
        fixture(style = SplitPaneDividerStyle.GUTTER) { scene ->
            assertEquals(8f, scene.tag("right").boundsInRoot.left - scene.tag("left").boundsInRoot.right)
            val image = scene.snapshot()
            assertEquals(4, (390..410).count { image.getRGB(it, 100) == border })
        }
    }

    @Test fun `hover highlights only the line and leaving restores it without moving panes`() = EventQueue.invokeAndWait {
        fixture { scene ->
            val before = scene.tag("left").boundsInRoot
            val seam = before.right.toInt()
            scene.sendPointerEvent(PointerEventType.Move, Offset(seam + 3f, 100f)); scene.frames()
            assertEquals(accent, scene.snapshot().getRGB(seam, 100))
            assertEquals(before, scene.tag("left").boundsInRoot)
            assertEquals(1, (seam - 4..seam + 4).count { scene.snapshot().getRGB(it, 100) == accent })
            scene.save("hover")
            scene.sendPointerEvent(PointerEventType.Move, Offset(20f, 100f)); scene.frames()
            assertEquals(border, scene.snapshot().getRGB(seam, 100))
            assertEquals(before, scene.tag("left").boundsInRoot)
        }
    }

    @Test fun `drag starts outside the painted line and remains active outside the moving target`() = EventQueue.invokeAndWait {
        fixture { scene ->
            val start = Offset(scene.tag("left").boundsInRoot.right + 3f, 100f)
            scene.sendPointerEvent(PointerEventType.Move, start)
            scene.sendPointerEvent(PointerEventType.Press, start, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Move, start + Offset(30f, 0f), buttons = PointerButtons(isPrimaryPressed = true)); scene.frames()
            scene.sendPointerEvent(PointerEventType.Move, start + Offset(90f, 0f), buttons = PointerButtons(isPrimaryPressed = true)); scene.frames()
            assertTrue(ratio.floatValue > .55f)
            val seam = scene.tag("left").boundsInRoot.right.toInt()
            assertEquals(accent, scene.snapshot().getRGB(seam, 100))
            scene.sendPointerEvent(PointerEventType.Move, Offset(2000f, 100f), buttons = PointerButtons(isPrimaryPressed = true)); scene.frames()
            assertEquals(.8f, ratio.floatValue)
            scene.sendPointerEvent(PointerEventType.Release, Offset(2000f, 100f), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Move, Offset(20f, 100f)); scene.frames()
            assertEquals(border, scene.snapshot().getRGB(scene.tag("left").boundsInRoot.right.toInt(), 100))
        }
    }

    @Test fun `keyboard resizing stays clamped and exposes a stable focusable range`() = EventQueue.invokeAndWait {
        fixture(width = 360) { scene ->
            assertTrue(scene.nodes().first { it.config.contains(SemanticsActions.RequestFocus) }.config[SemanticsActions.RequestFocus].action!!.invoke())
            scene.frames()
            scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown)); scene.frames()
            assertEquals(.52f, ratio.floatValue, .0001f)
            repeat(40) { scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown)); scene.frames() }
            assertEquals(.8f, ratio.floatValue)
            repeat(80) { scene.sendKeyEvent(KeyEvent(Key.DirectionLeft, KeyEventType.KeyDown)); scene.frames() }
            assertEquals(.2f, ratio.floatValue)
            val range = scene.handle().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(.2f.. .8f, range.range)
            assertTrue(scene.tag("left").boundsInRoot.width > 0)
            assertTrue(scene.tag("right").boundsInRoot.width > 0)
            scene.save("compact-keyboard")
        }
    }

    private fun fixture(density: Float = 1f, style: SplitPaneDividerStyle = SplitPaneDividerStyle.SINGLE_LINE,
        width: Int = 800, check: (ImageComposeScene) -> Unit) {
        ratio.floatValue = .5f
        ImageComposeScene(width, 240, Density(density), content = { KNetTheme {
            border = KNetTheme.colors.border.toArgb(); accent = KNetTheme.colors.accent.toArgb()
            HorizontalSplitPane(ratio.floatValue, { ratio.floatValue = it },
                firstPane = { Box(it.fillMaxSize().background(KNetTheme.colors.surface).testTag("left")) },
                secondPane = { Box(it.fillMaxSize().background(KNetTheme.colors.surface).testTag("right")) },
                dividerStyle = style, minSplitRatio = .2f, maxSplitRatio = .8f)
        }}).use { scene -> scene.frames(); check(scene) }
    }
    private fun ImageComposeScene.frames() { repeat(3) { render(++frame * 1_000_000_000).close() } }
    private fun ImageComposeScene.snapshot(): BufferedImage = render(++frame * 1_000_000_000).use { image ->
        image.encodeToData()!!.use { ImageIO.read(ByteArrayInputStream(it.bytes)) }
    }
    private fun ImageComposeScene.save(name: String) { ImageIO.write(snapshot(), "png", File("/tmp/knet-traffic-divider-$name.png")) }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
    private fun ImageComposeScene.tag(tag: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    private fun ImageComposeScene.handle() = nodes().first { it.config.contains(SemanticsProperties.ProgressBarRangeInfo) }
}
