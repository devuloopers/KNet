package com.devuloopers.knet.ui.desktop.httppanel

import androidx.compose.ui.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.model.*
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.ResponseViewPanel
import java.awt.EventQueue
import kotlin.test.*

/** A captured HTTP error response remains inspectable; transport failures retain their own diagnostics. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ResponseFailurePresentationTest {
    @Test fun `captured gateway errors keep response headers and viewer instead of network error card`() = EventQueue.invokeAndWait {
        for (status in listOf(502, 503, 504)) {
            ImageComposeScene(640, 700, content = { KNetTheme {
                ResponseViewPanel(ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(status), "Gateway response",
                    listOf(HeaderField(HeaderName("x-captured-response"), "retained-server-header"))),
                    activeSubTab = InspectorSubTab.HEADERS, showSummaryHeader = false)
            }}).use { scene ->
                scene.render(1_000_000_000).close()
                val texts = scene.texts()
                assertTrue("retained-server-header" in texts, texts.toString())
                assertFalse(texts.any { it == "No Response Received" || it == "Network Error" })
            }
        }
    }
    @Test fun `explicit transport timeout shows its diagnostic and body only preview tolerates missing head`() = EventQueue.invokeAndWait {
        ImageComposeScene(640, 700, content = { KNetTheme {
            ResponseViewPanel(null, failureReason = NetworkFailureReason.Timeout(5_000, "timed out"))
        }}).use { scene ->
            scene.render(1_000_000_000).close(); assertTrue("Request Execution Timed Out" in scene.texts())
        }
        ImageComposeScene(640, 700, content = { KNetTheme {
            ResponseViewPanel(null, payloadSpec = PayloadInspectionSpec.fromPayload(emptyList(), "retained preview"))
        }}).use { scene -> scene.render(1_000_000_000).close(); assertTrue("Body" in scene.texts()) }
    }
    @Test fun `changing response keeps its navigation while detail is preparing`() = EventQueue.invokeAndWait {
        val preparing = mutableStateOf(false)
        ImageComposeScene(640, 700, content = { KNetTheme {
            ResponseViewPanel(if (preparing.value) null else ResponseHead(ApplicationProtocol.fromToken("HTTP/2"), HttpStatus(200), headers = emptyList()),
                isPreparing = preparing.value, showSummaryHeader = false)
        }}).use { scene ->
            scene.render(1_000_000_000).close()
            assertTrue("Body" in scene.texts())
            preparing.value = true
            scene.render(2_000_000_000).close()
            assertTrue("Body" in scene.texts(), "Response navigation must remain mounted during loading.")
            assertFalse("No Response Received" in scene.texts())
        }
    }

    private fun ImageComposeScene.texts(): List<String> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
}
