package com.devuloopers.knet.ui.desktop.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsTab
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import com.devuloopers.knet.ui.desktop.networkconditions.view.NetworkConditionsQuickControl
import com.devuloopers.knet.ui.desktop.networkconditions.view.NetworkThroughputIndicator
import com.devuloopers.knet.ui.desktop.traffic.model.CaptureState
import com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbar
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarActions
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficToolbarState
import java.awt.EventQueue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Actual shell-composed Traffic footer, using the same feature-owned readings as Overview. */
@OptIn(ExperimentalComposeUiApi::class)
class TrafficThroughputFooterTest {
    private var frameNanos = 0L

    @Test
    fun `footer readings work while conditions are off stay independent of filters and open overview`() = onUiThread {
        val history = mutableStateOf(NetworkThroughputHistory())
        val matchingRows = mutableStateOf(128L)
        var opened = false
        var selected = NetworkConditionsTab.PROFILES
        ImageComposeScene(1280, 700, content = {
            KNetTheme {
                Column(Modifier.fillMaxSize()) {
                    TrafficToolbar(TrafficToolbarState(captureState = CaptureState.PAUSED), TrafficToolbarActions(),
                        networkConditionsControl = { NetworkConditionsQuickControl(NetworkConditionConfiguration(), {}, {}, {}) })
                    TrafficTable(emptyList(), null, false, {}, "4.82 MB", matchingRows.value, Modifier.weight(1f),
                        throughputIndicator = { NetworkThroughputIndicator(history.value, {
                            selected = NetworkConditionsTab.OVERVIEW
                            opened = true
                        }) })
                }
            }
        }).use { scene ->
            scene.frame()
            val height = scene.footer().boundsInRoot.height
            assertEquals(48f, height)
            assertTrue(scene.indicator().description().contains("Download 0 kbps, upload 0 kbps"))
            val initialBounds = scene.indicator().boundsInRoot
            assertTrue(scene.nodes().any { it.text().contains("Conditions: Off") })
            history.value = measured(128_000L, 24_000L)
            scene.frame()
            assertTrue(scene.indicator().description().contains("Download 128 kbps, upload 24 kbps"))
            assertEquals(initialBounds, scene.indicator().boundsInRoot, "The first sample must not move or resize the readings")
            val measuredWidth = scene.indicator().boundsInRoot.width
            scene.save("traffic-throughput-live")

            matchingRows.value = 2L
            scene.frame()
            assertTrue(scene.nodes().any { it.text().contains("0 of 2 Requests") })
            assertTrue(scene.indicator().description().contains("Download 128 kbps, upload 24 kbps"))
            history.value = measured(1_600_000L, 12_500L)
            scene.frame()
            assertEquals(measuredWidth, scene.indicator().boundsInRoot.width, "Changing normal rate units must not shift adjacent statistics")
            assertTrue(scene.indicator().description().contains("Download 1.6 Mbps, upload 12.5 kbps"))
            assertEquals(height, scene.footer().boundsInRoot.height)
            assertTrue(assertNotNull(scene.indicator().config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
            assertTrue(opened)
            assertEquals(NetworkConditionsTab.OVERVIEW, selected)

            history.value = measured(0L, 0L)
            scene.frame()
            assertTrue(scene.indicator().description().contains("Download 0 kbps, upload 0 kbps"))
            scene.save("traffic-throughput-idle")
            history.value = NetworkThroughputHistory()
            scene.frame()
            assertTrue(scene.indicator().description().contains("Download 0 kbps, upload 0 kbps"))
            assertEquals(initialBounds, scene.indicator().boundsInRoot)
            history.value = NetworkThroughputHistory(sampleCount = 1)
            scene.frame()
            assertTrue(scene.indicator().description().contains("Download —, upload —"))
            assertEquals(initialBounds, scene.indicator().boundsInRoot, "A later unavailable interval also retains stable geometry")
            assertFalse(scene.nodes().any { it.text() == "Actual throughput" })
        }
    }

    @Test
    fun `narrow larger text footer retains its height and exposes overflow instead of clipping statistics`() = onUiThread {
        val visible = mutableStateOf(false)
        ImageComposeScene(360, 560, Density(1f, 1.5f), content = {
            KNetTheme {
                TrafficTable(emptyList(), null, false, {}, "4.82 MB", 128L,
                    throughputIndicator = if (visible.value) ({ NetworkThroughputIndicator(measured(128_000L, 24_000L), {}) }) else null)
            }
        }).use { scene ->
            scene.frame()
            val originalHeight = scene.footer().boundsInRoot.height
            visible.value = true
            scene.frame()
            assertEquals(originalHeight, scene.footer().boundsInRoot.height)
            assertEquals(48f, originalHeight)
            val footerNodes = descendants(scene.footer())
            val overflowing = footerNodes.first { node ->
                node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.maxValue?.invoke()?.let { it > 0f } == true
            }
            assertTrue(assertNotNull(overflowing.config.getOrNull(SemanticsActions.ScrollBy)?.action).invoke(10_000f, 0f))
            scene.frame()
            assertTrue(scene.nodes().any { it.text() == "4.82 MB" && it.boundsInRoot.right <= 360f })
            assertTrue(scene.indicator().description().contains("Download 128 kbps, upload 24 kbps"))
            scene.save("traffic-throughput-narrow")
        }
    }

    private fun measured(download: Long, upload: Long) = NetworkThroughputHistory(
        LongArray(60) { download }, LongArray(60) { upload }, BooleanArray(60) { true }, 60)
    private fun ImageComposeScene.footer() = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "Traffic statistics" }
    private fun ImageComposeScene.indicator() = nodes().first { it.description().startsWith("Actual KNet throughput.") }
    private fun SemanticsNode.description() = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
    private fun ImageComposeScene.frame() { repeat(2) { frameNanos += 1_000_000_000L; render(frameNanos).close() } }
    private fun ImageComposeScene.save(name: String) {
        val directory = File("build/reports/network-conditions-ui").apply { mkdirs() }
        frameNanos += 1_000_000_000L
        render(frameNanos).use { image -> image.encodeToData()?.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }
    private fun onUiThread(block: () -> Unit) = EventQueue.invokeAndWait(block)
}
