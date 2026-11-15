package com.devuloopers.knet.ui.desktop.apistudio.export

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.ResponseSummaryHeader
import com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportButton
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.*
import java.awt.EventQueue
import kotlin.test.*

/** Actual Compose semantics verify stable mounted export controls across response eligibility changes. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CaptureExportRenderTest {
    @Test fun `response export button keeps identity and bounds before response and across all status classes`() {
        EventQueue.invokeAndWait {
            for (width in listOf(420, 980)) {
                val status = mutableStateOf<Int?>(null)
                ImageComposeScene(width, 100, content = { KNetTheme {
                    ResponseSummaryHeader(status.value?.let { ResponseHead(ApplicationProtocol.fromToken("HTTP/1.1"), HttpStatus(it), "", emptyList()) },
                        ExchangeTimings(totalMillis = 15), "0 B", exportAction = { CaptureExportButton(status.value != null) {} })
                } }).use { scene ->
                    var time = 0L
                    fun frame() { scene.render(time.also { time += 250_000_000 }).close() }
                    fun nodes(): List<SemanticsNode> {
                        fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
                        return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
                    }
                    fun button() = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Export" } == true }
                    frame(); val original = button().let { it.id to it.boundsInRoot }
                    for (code in listOf(200, 204, 304, 400, 500, 502, 503, 504)) {
                        status.value = code
                        repeat(3) { frame(); assertEquals(original, button().let { it.id to it.boundsInRoot }) }
                    }
                    status.value = null; frame(); assertEquals(original, button().let { it.id to it.boundsInRoot })
                }
            }
        }
    }
}
