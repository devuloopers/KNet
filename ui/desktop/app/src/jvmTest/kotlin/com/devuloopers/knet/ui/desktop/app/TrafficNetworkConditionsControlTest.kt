package com.devuloopers.knet.ui.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.use
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.desktop.networkconditions.view.NetworkConditionsQuickControl
import com.devuloopers.knet.ui.desktop.traffic.model.CaptureState
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

@OptIn(ExperimentalComposeUiApi::class)
class TrafficNetworkConditionsControlTest {
    private var frameNanos = 0L

    @Test
    fun `global shortcut has one unlimited choice preserves old IDs and keeps custom profiles and rules`() = onUiThread {
        for (savedId in listOf(null, NetworkConditionBuiltIns.NO_THROTTLING.id)) {
            val custom = NetworkConditionBuiltIns.NO_THROTTLING.copy(id = NetworkConditionProfileId("company-baseline"),
                name = "Company baseline", builtIn = false)
            val rule = NetworkConditionRule(NetworkConditionRuleId("retained-exception"),
                NetworkConditionTarget.parse("unlimited.example.test", 443), NetworkConditionBuiltIns.NO_THROTTLING.id)
            val configuration = mutableStateOf(NetworkConditionConfiguration(globalProfileId = savedId,
                customProfiles = listOf(custom), rules = listOf(rule)))
            val activations = mutableListOf<NetworkConditionProfileId?>()
            ImageComposeScene(1280, 700, content = {
                KNetTheme(motion = Motion(animationsEnabled = false)) {
                    NetworkConditionsQuickControl(configuration.value, {}, {
                        activations += it
                        configuration.value = configuration.value.copy(enabled = true, globalProfileId = it)
                    }, {})
                }
            }).use { scene ->
                scene.frame(); scene.click(scene.quickAnchor()); scene.frame()
                scene.clickText(NetworkConditionBuiltIns.NO_THROTTLING.name); scene.frame()
                val options = scene.nodes().filter { it.config.getOrNull(SemanticsProperties.Selected) != null }
                assertEquals(1, options.count { it.text() == NetworkConditionBuiltIns.NO_THROTTLING.name })
                assertFalse(options.any { it.text() == "No global throttling" || it.text() == "Domain rules only" || it.text() == "Lossy / unstable" })
                assertTrue(options.any { it.text() == "Unstable connection" })
                assertTrue(options.any { it.text() == "Company baseline" }, "Custom unlimited profiles keep their identity")
                assertTrue(activations.isEmpty())
                assertEquals(savedId, configuration.value.globalProfileId)
                scene.click(options.single { it.text() == NetworkConditionBuiltIns.NO_THROTTLING.name }); scene.frame()
                assertEquals(listOf<NetworkConditionProfileId?>(null), activations)
                assertTrue(configuration.value.enabled)
                assertEquals(listOf(rule), configuration.value.rules)
                assertEquals(listOf(custom), configuration.value.customProfiles)
                assertTrue(scene.quickAnchor().text().contains("Global: ${NetworkConditionBuiltIns.NO_THROTTLING.name}"))
                scene.save("traffic-global-preset-cleanup-${savedId?.value ?: "unset"}")
            }
        }
    }

    @Test
    fun `selected label expands and contracts smoothly while auto scroll stays anchored`() = onUiThread {
        val configuration = mutableStateOf(NetworkConditionConfiguration())
        val custom = NetworkConditionBuiltIns.STREAMING_100_KBPS.copy(
            id = NetworkConditionProfileId("long-label"),
            name = "A very long company streaming profile that must not crowd capture controls",
            builtIn = false,
        )
        ImageComposeScene(1440, 560, content = {
            KNetTheme {
                Box(Modifier.fillMaxSize().background(KNetTheme.colors.background)) {
                    TrafficToolbar(TrafficToolbarState(), TrafficToolbarActions(), networkConditionsControl = {
                        NetworkConditionsQuickControl(configuration.value, {}, {}, {})
                    })
                }
            }
        }).use { scene ->
            scene.frame()
            val off = scene.quickAnchor().boundsInRoot
            scene.assertLabelFits("Conditions: Off")
            val auto = scene.nodes().first { it.text().contains("Auto scroll") }.boundsInRoot

            configuration.value = configuration.value.copy(customProfiles = listOf(custom))
            scene.frame()
            assertEquals(off.width, scene.quickAnchor().boundsInRoot.width,
                "An unselected long profile must not reserve width")

            configuration.value = configuration.value.copy(enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
            scene.advanceBy(1)
            scene.advanceBy(16)
            scene.advanceBy(60)
            val expanding = scene.quickAnchor().boundsInRoot
            scene.frame()
            val expanded = scene.quickAnchor().boundsInRoot
            scene.assertLabelFits("Global: Streaming 100 kbps")
            assertTrue(expanding.width > off.width && expanding.width < expanded.width,
                "Expansion must have an intermediate width, not jump to the selected label size")
            assertEquals(off.right, expanding.right)
            assertEquals(off.height, expanding.height)
            assertEquals(off.height, expanded.height)
            assertEquals(off.right, expanded.right)
            assertEquals(auto, scene.nodes().first { it.text().contains("Auto scroll") }.boundsInRoot)

            configuration.value = configuration.value.copy(globalProfileId = custom.id)
            scene.frame()
            scene.frame()
            val bounded = scene.quickAnchor().boundsInRoot
            assertTrue(bounded.width > expanded.width, "A longer selected name must grow beyond a preset: $bounded / $expanded")
            val clear = scene.nodes().first { it.text() == "Clear" }.boundsInRoot
            assertTrue(clear.right <= bounded.left, "Long selections must not overlap capture controls")
            scene.save("traffic-toolbar-long-selection")

            configuration.value = configuration.value.copy(enabled = false)
            scene.advanceBy(1)
            scene.advanceBy(16)
            scene.advanceBy(60)
            val contracting = scene.quickAnchor().boundsInRoot
            scene.frame()
            assertTrue(contracting.width < bounded.width && contracting.width > off.width,
                "Contraction should pass through an intermediate width: contracting=$contracting, long=$bounded, off=$off")
            assertEquals(off.right, contracting.right)
            assertEquals(off.height, contracting.height)
            assertEquals(off, scene.quickAnchor().boundsInRoot)
            scene.assertLabelFits("Conditions: Off")
            assertEquals(auto, scene.nodes().first { it.text().contains("Auto scroll") }.boundsInRoot)
            scene.save("traffic-toolbar-off-selection")
        }
    }

    @Test
    fun `reduced motion applies the selected width without a transition`() = onUiThread {
        val configuration = mutableStateOf(NetworkConditionConfiguration())
        ImageComposeScene(1280, 560, content = {
            KNetTheme(motion = Motion(animationsEnabled = false)) {
                TrafficToolbar(TrafficToolbarState(), TrafficToolbarActions(), networkConditionsControl = {
                    NetworkConditionsQuickControl(configuration.value, {}, {}, {})
                })
            }
        }).use { scene ->
            scene.frame()
            val offWidth = scene.quickAnchor().boundsInRoot.width
            configuration.value = configuration.value.copy(enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
            scene.advanceBy(1)
            scene.advanceBy(1)
            val immediateWidth = scene.quickAnchor().boundsInRoot.width
            scene.frame()
            assertTrue(immediateWidth > offWidth)
            assertEquals(immediateWidth, scene.quickAnchor().boundsInRoot.width)
            configuration.value = configuration.value.copy(enabled = false)
            scene.advanceBy(1)
            scene.advanceBy(1)
            assertEquals(offWidth, scene.quickAnchor().boundsInRoot.width)
        }
    }

    @Test
    fun `global control fits before auto scroll with long status at minimum width and larger text`() = onUiThread {
        val custom = NetworkConditionBuiltIns.STREAMING_100_KBPS.copy(id = NetworkConditionProfileId("toolbar-custom"),
            name = "Company streaming test with a very long custom profile name", builtIn = false)
        val configuration = NetworkConditionConfiguration(enabled = true, globalProfileId = custom.id, customProfiles = listOf(custom))
        ImageComposeScene(976, 560, Density(1f, 1.25f), content = {
            KNetTheme {
                Box(Modifier.fillMaxSize().background(KNetTheme.colors.background)) {
                    TrafficToolbar(
                        TrafficToolbarState(captureState = CaptureState.PAUSED,
                            engineState = ProxyRuntimeState.Failed("A long operational status that must truncate without hiding controls", true)),
                        TrafficToolbarActions(),
                        networkConditionsControl = { NetworkConditionsQuickControl(configuration, {}, {}, {}) },
                    )
                }
            }
        }).use { scene ->
            scene.frame()
            val quick = scene.quickAnchor()
            val auto = scene.nodes().first { it.text().contains("Auto scroll") }
            assertTrue(quick.boundsInRoot.right <= auto.boundsInRoot.left, "Global control must precede Auto scroll")
            assertTrue(auto.boundsInRoot.right <= 976, "Auto scroll must stay inside the toolbar")
            assertTrue(scene.nodes().any { it.text() == "Clear" && it.boundsInRoot.width > 0 })
            assertTrue(scene.nodes().first { it.text() == "Clear" }.boundsInRoot.right <= quick.boundsInRoot.left)
            assertTrue(scene.nodes().any { it.text() == "Start Capture" && it.boundsInRoot.width > 0 })
            scene.save("traffic-toolbar-compact")
        }
    }

    @Test
    fun `profile selection enables shared conditions and off preserves rules while capture is paused`() = onUiThread {
        val rule = NetworkConditionRule(NetworkConditionRuleId("toolbar-rule"),
            NetworkConditionTarget.parse("api.example.test", 443), NetworkConditionBuiltIns.SLOW_3G.id)
        val configuration = mutableStateOf(NetworkConditionConfiguration(globalProfileId = NetworkConditionBuiltIns.FAST_3G.id,
            rules = listOf(rule)))
        val activations = mutableListOf<NetworkConditionProfileId?>()
        var opened = false
        ImageComposeScene(1280, 800, content = {
            KNetTheme {
                Box(Modifier.fillMaxSize().background(KNetTheme.colors.background)) {
                    TrafficToolbar(TrafficToolbarState(captureState = CaptureState.PAUSED), TrafficToolbarActions(),
                        networkConditionsControl = {
                            NetworkConditionsQuickControl(
                                configuration.value,
                                onEnabledChange = { configuration.value = configuration.value.copy(enabled = it) },
                                onActivateProfile = {
                                    activations += it
                                    configuration.value = configuration.value.copy(enabled = true, globalProfileId = it)
                                },
                                onOpenNetworkConditions = { opened = true },
                            )
                        })
                }
            }
        }).use { scene ->
            scene.frame()
            assertTrue(scene.quickAnchor().text().contains("Conditions: Off"))
            scene.click(scene.quickAnchor())
            scene.frame()
            scene.save("traffic-global-control")
            scene.clickText("Fast 3G")
            scene.frame()
            scene.clickText("Streaming 100 kbps")
            scene.frame()
            assertEquals(listOf<NetworkConditionProfileId?>(NetworkConditionBuiltIns.STREAMING_100_KBPS.id), activations)
            assertTrue(configuration.value.enabled)
            assertTrue(scene.quickAnchor().text().contains("Global: Streaming 100 kbps"))
            assertEquals(listOf(rule), configuration.value.rules)

            // A full-screen configuration change is immediately reflected by the same toolbar state.
            configuration.value = configuration.value.copy(globalProfileId = NetworkConditionBuiltIns.NO_THROTTLING.id)
            scene.frame()
            assertTrue(scene.quickAnchor().text().contains("Global: ${NetworkConditionBuiltIns.NO_THROTTLING.name}"))
            scene.click(scene.quickAnchor())
            scene.frame()
            scene.clickText("Enable Network Conditions")
            scene.frame()
            assertFalse(configuration.value.enabled)
            assertEquals(NetworkConditionBuiltIns.NO_THROTTLING.id, configuration.value.globalProfileId)
            assertEquals(listOf(rule), configuration.value.rules)
            scene.clickText("Open Network Conditions")
            scene.frame()
            assertTrue(opened)
        }
    }

    private fun ImageComposeScene.quickAnchor(): SemanticsNode = nodes().first {
        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { label -> label.startsWith("Network Conditions quick control.") }
    }

    private fun ImageComposeScene.assertLabelFits(label: String) {
        val textNode = nodes().first { it.text() == label && it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action != null }
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(assertNotNull(textNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(results))
        assertTrue(results.isNotEmpty())
        assertFalse(results.any { it.hasVisualOverflow || it.isLineEllipsized(0) }, "The selected label must fit: $label")
    }

    private fun ImageComposeScene.clickText(text: String) = click(nodes().first {
        it.text() == text && it.config.getOrNull(SemanticsActions.OnClick)?.action != null
    })

    private fun ImageComposeScene.click(node: SemanticsNode) {
        assertNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action)
        val position = node.boundsInWindow.center
        sendPointerEvent(PointerEventType.Move, position)
        sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, buttons = PointerButtons(), button = PointerButton.Primary)
    }

    private fun ImageComposeScene.frame() {
        repeat(2) { frameNanos += 1_000_000_000L; render(frameNanos).close() }
    }

    private fun ImageComposeScene.advanceBy(milliseconds: Long) {
        frameNanos += milliseconds * 1_000_000L
        render(frameNanos).close()
    }

    private fun ImageComposeScene.save(name: String) {
        val directory = File("build/reports/network-conditions-ui").apply { mkdirs() }
        frameNanos += 1_000_000_000L
        render(frameNanos).use { image -> image.encodeToData()?.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        descendants(owner.rootSemanticsNode)
    }

    private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun onUiThread(block: () -> Unit) = EventQueue.invokeAndWait(block)
}
