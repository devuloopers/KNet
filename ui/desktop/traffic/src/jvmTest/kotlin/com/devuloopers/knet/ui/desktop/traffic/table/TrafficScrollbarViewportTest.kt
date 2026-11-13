package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.toTrafficRowUiState
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.*

/** Rendered viewport tests catch text/selection painting into pinned scrollbar chrome and header drift. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TrafficScrollbarViewportTest {
    private var nanos = 0L
    private var background = 0
    private var clearance = 0
    private var accent = 0

    @Test fun `wide table reserves one shared header and row gutter with no content under scrollbar`() = EventQueue.invokeAndWait {
        fixture(1200) { scene, _ ->
            assertGutter(scene, 1200)
            val header = scene.header()
            assertEquals(1200f - clearance, header.boundsInRoot.right)
            assertTrue(scene.nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.any { it.text == "Duration" })
            scene.save("wide")
        }
    }

    @Test fun `horizontal and vertical scrolling retain clipped content at narrow larger text viewport`() = EventQueue.invokeAndWait {
        fixture(680, fontScale = 1.5f) { scene, _ ->
            assertGutter(scene, 680)
            val headerScroll = scene.nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) && it.boundsInRoot.top == 0f }
            assertTrue(headerScroll.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0)
            assertTrue(headerScroll.config[SemanticsActions.ScrollBy].action!!.invoke(10_000f, 0f))
            scene.frames()
            assertTrue(scene.nodes().first { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) && it.boundsInRoot.top == 0f }
                .config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0)
            assertGutter(scene, 680)
            val vertical = scene.nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            assertTrue(vertical.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 700f))
            scene.frames()
            assertGutter(scene, 680)
            assertEquals(680f - clearance, scene.header().boundsInRoot.right)
            scene.save("narrow-scrolled-larger-text")
        }
    }

    @Test fun `filtering to a short or empty list removes the scrollbar and its gutter and restores on overflow`() = EventQueue.invokeAndWait {
        fixture(1200) { scene, rows ->
            assertGutter(scene, 1200)
            val overflowing = rows.value
            rows.value = overflowing.take(2); scene.frames()
            assertTrue(scene.nodes().none { "Scroll traffic rows" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
            assertEquals(1200f, scene.header().boundsInRoot.right)
            rows.value = emptyList(); scene.frames()
            assertTrue(scene.nodes().none { "Scroll traffic rows" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
            assertEquals(1200f, scene.header().boundsInRoot.right)
            rows.value = overflowing; scene.frames()
            assertGutter(scene, 1200)
        }
    }

    @Test fun `selected row has a complete viewport frame before the scrollbar at every horizontal offset`() = EventQueue.invokeAndWait {
        for (width in listOf(1200, 680)) fixture(width) { scene, _ ->
            scene.assertSelectedFrame(width)
            val header = scene.header()
            header.config[SemanticsActions.ScrollBy].action!!.invoke(10_000f, 0f)
            scene.frames()
            scene.assertSelectedFrame(width)
            scene.header().config[SemanticsActions.ScrollBy].action!!.invoke(-10_000f, 0f)
            scene.frames()
            scene.assertSelectedFrame(width)
            scene.save("complete-selection-$width")
        }
    }

    @Test fun `viewport frame follows selection and vertical scroll without leaving stale edges`() = EventQueue.invokeAndWait {
        val selected = mutableStateOf<String?>("row-4")
        fixture(680, selected = selected) { scene, _ ->
            scene.assertSelectedFrame(680)
            selected.value = "row-7"; scene.frames()
            scene.assertSelectedFrame(680)
            val vertical = scene.nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            vertical.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 100f)
            scene.frames()
            scene.assertSelectedFrame(680)
            selected.value = null; scene.frames()
            val image = scene.snapshot()
            val edge = scene.header().boundsInRoot.right.toInt() - 1
            for (y in 40 until 450) assertNotEquals(accent, image.getRGB(edge, y), "Stale selected outline at $y")
        }
    }

    private fun ImageComposeScene.assertSelectedFrame(width: Int) {
        val selected = nodes().single { it.config.getOrNull(SemanticsProperties.Selected) == true }.boundsInRoot
        val right = header().boundsInRoot.right.toInt()
        assertEquals(width - clearance, right)
        val image = snapshot()
        val middleY = selected.center.y.toInt()
        val middleX = right / 2
        assertEquals(accent, image.getRGB(0, middleY), "Missing left selection edge")
        assertEquals(accent, image.getRGB(right - 1, middleY), "Missing right selection edge before scrollbar")
        assertEquals(accent, image.getRGB(middleX, selected.top.toInt()), "Missing top selection edge")
        assertEquals(accent, image.getRGB(middleX, selected.bottom.toInt() - 1), "Missing bottom selection edge")
        assertGutter(this, width)
    }

    private fun fixture(width: Int, fontScale: Float = 1f,
        selected: androidx.compose.runtime.MutableState<String?> = mutableStateOf("row-4"),
        check: (ImageComposeScene, androidx.compose.runtime.MutableState<List<com.devuloopers.knet.ui.desktop.traffic.model.TrafficRowUiState>>) -> Unit) {
        val rows = mutableStateOf((1..60).map { index -> HttpExchangeSnapshot(ExchangeId("row-$index"),
            request = HttpRequestSnapshot(RequestHead(HttpMethod.POST, RequestTarget.Absolute(HttpScheme.fromToken("https"),
                Authority("api.example.test", 443), "/events/$index"), ApplicationProtocol.fromToken("HTTP/1.1"), emptyList())),
            response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), "OK", emptyList())),
            state = ExchangeState.COMPLETED, startedAtEpochMillis = index * 1_000L,
            timings = ExchangeTimings(totalMillis = 752)).toTrafficRowUiState() })
        ImageComposeScene(width, 500, Density(1f, fontScale), content = { KNetTheme {
            background = KNetTheme.colors.background.toArgb()
            accent = KNetTheme.colors.accent.toArgb()
            clearance = (KNetTheme.dimensions.scrollbarWidth.value + KNetTheme.spacing.sm.value).roundToInt()
            TrafficTable(rows.value, selected.value, false, {}, "256.76 KB", rows.value.size.toLong())
        }}).use { scene -> scene.frames(); check(scene, rows) }
    }
    private fun assertGutter(scene: ImageComposeScene, width: Int) {
        val scrollbar = scene.nodes().first { "Scroll traffic rows" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }.boundsInRoot
        val end = scene.header().boundsInRoot.right.toInt()
        assertEquals(width - clearance, end)
        assertTrue(scrollbar.left > end)
        val image = scene.snapshot()
        // Every row pixel in the breathing-space lane stays clear, including the selected row outline.
        for (x in end until scrollbar.left.toInt()) for (y in scrollbar.top.toInt() + 2 until scrollbar.bottom.toInt() - 2)
            assertEquals(background, image.getRGB(x, y), "Content painted into scrollbar clearance at x=$x y=$y")
    }
    private fun ImageComposeScene.frames() { repeat(5) { render(++nanos * 1_000_000_000).close() } }
    private fun ImageComposeScene.snapshot() = render(++nanos * 1_000_000_000).use { image ->
        image.encodeToData()!!.use { ImageIO.read(ByteArrayInputStream(it.bytes)) }
    }
    private fun ImageComposeScene.save(name: String) { ImageIO.write(snapshot(), "png", File("/tmp/knet-traffic-scrollbar-$name.png")) }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
    private fun ImageComposeScene.header() = nodes().first { "Scroll traffic column headers" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
}
