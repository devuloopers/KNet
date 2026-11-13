package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.components.split.SplitPaneDividerStyle
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.*
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

/** Actual table/header/footer and inspector composition must not reintroduce a second vertical frame. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TrafficSingleDividerTest {
    @Test fun `Traffic table and inspector share one uninterrupted divider at wide and compact sizes`() = EventQueue.invokeAndWait {
        for ((width, height, fontScale) in listOf(Triple(1440, 800, 1f), Triple(1024, 600, 1.25f))) {
            val exchange = HttpExchangeSnapshot(ExchangeId("selected"),
                request = HttpRequestSnapshot(RequestHead(HttpMethod.POST, RequestTarget.Absolute(
                    HttpScheme.fromToken("https"), Authority("api.example.test", 443), "/sessions/v2/track"),
                    ApplicationProtocol.fromToken("HTTP/1.1"), emptyList())),
                response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), "OK", emptyList())),
                state = ExchangeState.COMPLETED, startedAtEpochMillis = 1_000,
                timings = ExchangeTimings(totalMillis = 547))
            val rows = (1..35).map { index -> exchange.copy(id = ExchangeId("row-$index"),
                startedAtEpochMillis = index * 1_000L).toTrafficRowUiState() }
            val selected = rows[3]
            val ratio = mutableFloatStateOf(.58f)
            var border = 0
            ImageComposeScene(width, height, Density(1f, fontScale), content = { KNetTheme {
                border = KNetTheme.colors.border.toArgb()
                HorizontalSplitPane(ratio.floatValue, { ratio.floatValue = it }, dividerStyle = SplitPaneDividerStyle.SINGLE_LINE,
                    firstPane = { pane -> TrafficTable(rows, selected.transactionId, false, {}, "256.76 KB", rows.size.toLong(),
                        modifier = pane.testTag("Traffic list")) },
                    secondPane = { pane -> TrafficInspectorPanel(selected, InspectorTab.OVERVIEW,
                        preparedState = InspectorPreparedState(selected.transactionId, exchange.copy(id = ExchangeId(selected.transactionId)), loadState = InspectorLoadState.READY),
                        onTabSelected = {}, modifier = pane.testTag("Traffic inspector")) })
            }}).use { scene ->
                repeat(4) { scene.render((it + 1L) * 1_000_000_000).close() }
                val nodes = scene.semanticsOwners.flatMap { owner ->
                    fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
                    descend(owner.rootSemanticsNode)
                }
                val list = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == "Traffic list" }.boundsInRoot
                val inspector = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == "Traffic inspector" }.boundsInRoot
                val seam = list.right.toInt()
                assertEquals(1f, inspector.left - list.right)
                scene.render(5_000_000_000).use { rendered -> rendered.encodeToData()!!.use { encoded ->
                    val image = ImageIO.read(ByteArrayInputStream(encoded.bytes))
                    for (y in 1 until height - 1) assertEquals(border, image.getRGB(seam, y), "Interrupted divider at $width x=$seam y=$y")
                    for (y in listOf(15, 160, height - 25)) {
                        assertNotEquals(border, image.getRGB(seam - 1, y), "Duplicate table frame at y=$y")
                        assertNotEquals(border, image.getRGB(seam + 1, y), "Duplicate inspector frame at y=$y")
                    }
                    File("/tmp/knet-traffic-single-divider-$width.png").writeBytes(encoded.bytes)
                }}
                // The scrollbar's right-hand edge must remain usable beside the overlaid splitter handle.
                val scrollbar = nodes.first { "Scroll traffic rows" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }.boundsInRoot
                assertTrue(scrollbar.right <= seam - 4)
                val press = Offset(scrollbar.right - 1f, scrollbar.top + 20f)
                scene.sendPointerEvent(PointerEventType.Move, press)
                scene.sendPointerEvent(PointerEventType.Press, press, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
                scene.sendPointerEvent(PointerEventType.Move, press + Offset(0f, 90f), buttons = PointerButtons(isPrimaryPressed = true))
                scene.sendPointerEvent(PointerEventType.Release, press + Offset(0f, 90f), button = PointerButton.Primary)
                repeat(4) { scene.render((it + 6L) * 1_000_000_000).close() }
                assertEquals(.58f, ratio.floatValue, "Scrollbar drag must not resize the split panes")
                val after = scene.semanticsOwners.flatMap { owner ->
                    fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
                    descend(owner.rootSemanticsNode)
                }.first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                assertTrue(after.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f, "The scrollbar thumb must scroll the list")

            }
        }
    }
}
