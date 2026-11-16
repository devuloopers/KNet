package com.devuloopers.knet.website

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlin.test.assertTrue

/** Fixed authored scenarios; identities and output names are closed rather than arbitrary file paths. */
internal enum class ArtworkScene(val id: String, val width: Int = 1440, val height: Int = 900, val density: Float = 1.25f) {
    TRAFFIC("traffic", 1600, 960), MULTI_DEVICE("multi-device", 1600, 960),
    API_STUDIO("api-studio", 1600, 960), INTERCEPTS("intercepts"),
    NETWORK_CONDITIONS("network-conditions"), PROTECTED_TRAFFIC("protected-traffic"),
    COMPANION_PAIRING("companion-pairing", 840, 1680, 2f),
    COMPANION_CONNECTED("companion-connected", 840, 1680, 2f),
}

/**
 * Renders real components on the AWT UI thread into the explicit output directory, with no app bootstrap.
 * Required semantic labels must be laid out inside the viewport before a PNG is accepted. Fixed scene time
 * settles transitions; the scenario state never subscribes to a clock, transport, database or device.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
internal fun renderArtwork(scene: ArtworkScene, requiredLabels: List<String>, content: @Composable () -> Unit) {
    EventQueue.invokeAndWait {
        lateinit var focusManager: FocusManager
        ImageComposeScene(scene.width, scene.height, Density(scene.density), content = {
            val currentFocusManager = LocalFocusManager.current
            SideEffect { focusManager = currentFocusManager }
            KNetTheme { Box(Modifier.fillMaxSize().background(KNetTheme.colors.background)) { content() } }
        }).use { composition ->
            repeat(16) {
                composition.render((it + 1L) * 250_000_000L).close()
                // Yield wall time too: syntax preparation runs on worker threads, independently of frame time.
                Thread.sleep(10)
            }
            focusManager.clearFocus(force = true)
            var stableFrames = 0
            var previous: ByteArray? = null
            var encoded = byteArrayOf()
            val deadline = System.nanoTime() + 5_000_000_000L
            // Keep scene time fixed after transitions have settled. Readiness requires a half-second of
            // identical decoded scene output, not an arbitrary number of fast frames during background work.
            while (stableFrames < 10 && System.nanoTime() < deadline) {
                composition.render(8_000_000_000L).use { image ->
                    image.encodeToData()!!.use { data -> encoded = data.bytes }
                }
                stableFrames = if (previous?.contentEquals(encoded) == true) stableFrames + 1 else 0
                previous = encoded
                Thread.sleep(50)
            }
            assertTrue(stableFrames >= 10, "${scene.id}: background rendering did not settle")
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            val nodes = composition.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
            for (label in requiredLabels) assertTrue(nodes.any { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                val bounds = node.boundsInRoot
                text.contains(label) && bounds.width > 0 && bounds.height > 0 && bounds.top >= 0 &&
                    bounds.left >= 0 && bounds.bottom <= scene.height && bounds.right <= scene.width
            }, "${scene.id}: required visible component is missing or clipped: $label")
            val directory = File(requireNotNull(System.getProperty("knet.website.artworkDirectory")))
            directory.mkdirs()
            val output = File(directory, "${scene.id}-preview.png")
            output.writeBytes(encoded)
            assertTrue(output.length() > 10_000, "${scene.id}: empty product render")
        }
    }
}

/** Safe shared JSON used by the genuine editor and response-viewer components. */
internal val sampleResponse = """{
  "data": {
    "products": [
      {
        "id": "1042",
        "name": "Wireless headphones",
        "price": 129.00,
        "inStock": true
      },
      {
        "id": "1043",
        "name": "Studio speakers",
        "price": 249.00,
        "inStock": true
      }
    ],
    "total": 2
  }
}"""
