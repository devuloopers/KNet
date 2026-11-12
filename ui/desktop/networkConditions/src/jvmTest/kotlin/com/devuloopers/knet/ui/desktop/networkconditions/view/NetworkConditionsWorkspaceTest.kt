package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.runtime.mutableStateOf
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionRuleDetails
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionRuleDraft
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsState
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsTab
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import java.awt.EventQueue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real Compose layout and semantics checks using the UI runtime already shipped by KNet. */
@OptIn(ExperimentalComposeUiApi::class)
class NetworkConditionsWorkspaceTest {
    private var frameNanos = 0L
    @Test
    fun `minimum window with larger text retains controls and first rule without the packet footer`() = onUiThread {
        render(stateWithRules(1000), 976, 560, fontScale = 1.25f, name = "compact-rules") { scene ->
            assertVisible(scene, "Add rule", 560)
            assertVisible(scene, "api0.example.test:443", 560)
            assertFalse(scene.nodes().any { it.text().contains("Packet conditions:") || it.text().contains("Android UDP experimental") })
            assertTrue(scene.nodes().count { it.text().contains("api") && it.text().contains("example.test") } < 40,
                "The table must compose only visible rows, not the entire rule list")
            assertFalse(scene.nodes().any { it.text() == "Actual throughput" })
            assertTrue(scene.scrollableTableHeight() > 560 / 2f, "Rules need at least half the compact viewport")
        }
    }

    @Test
    fun `profile tab retains custom editing and protects builtin profiles`() = onUiThread {
        val initial = stateWithRules(3)
        val custom = NetworkConditionBuiltIns.STREAMING_100_KBPS.copy(
            id = NetworkConditionProfileId("test-custom"), name = "Test custom", builtIn = false,
        )
        val selected = mutableStateOf(initial.copy(configuration = initial.configuration.copy(customProfiles = listOf(custom))))
        ImageComposeScene(1440, 900, content = {
            KNetTheme {
                Workspace(selected.value, onTab = { selected.value = selected.value.copy(activeTab = it) })
            }
        }).use { scene ->
            scene.frame()
            val tab = scene.nodes().first { it.text() == "Profiles" && it.config.getOrNull(SemanticsActions.OnClick) != null }
            assertTrue(assertNotNull(tab.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
            scene.frame()
            assertEquals(NetworkConditionsTab.PROFILES, selected.value.activeTab)
            assertVisible(scene, "New custom profile", 900)
            val scrollToIndex = scene.nodes().firstNotNullOf { it.config.getOrNull(SemanticsActions.ScrollToIndex)?.action }
            assertTrue(scrollToIndex(selected.value.configuration.profiles.lastIndex))
            scene.frame()
            val descriptions = scene.nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue("Edit Test custom" in descriptions)
            assertFalse("Edit No throttling" in descriptions)
            scene.save("profiles")
        }
    }

    @Test
    fun `default overview shows waiting state and global controls without unrelated add actions`() = onUiThread {
        render(NetworkConditionsState(), 1280, 800, name = "empty-bypassed") { scene ->
            listOf("Overview", "Rules", "Profiles", "Reset", "Disabled").forEach { assertVisible(scene, it, 800) }
            assertFalse(scene.nodes().any { it.text() == "Add rule" || it.text() == "New custom profile" })
            assertTrue(scene.nodes().any { it.text().contains("Waiting for traffic") })
            assertTrue(scene.nodes().any { it.text().contains("Conditions bypassed") })
            assertFalse(scene.nodes().any { it.text().contains("Packet conditions:") })
        }
    }

    @Test
    fun `overview groups configured and measured values and rules keep an actionable empty panel`() = onUiThread {
        val state = NetworkConditionsState(
            configuration = NetworkConditionConfiguration(enabled = true, globalProfileId = NetworkConditionBuiltIns.SLOW_3G.id),
            runtime = NetworkConditionRuntimeSnapshot(activeFlows = 11, queuedBytes = 53L,
                downloadedBytes = 977L * 1_024L, uploadedBytes = 3L * 1_024L * 1_024L),
            throughput = NetworkThroughputHistory(LongArray(60) { 12_000L },
                LongArray(60) { index -> if (index % 25 in 7..13 || index >= 50) 385_000L else 20_000L },
                BooleanArray(60) { true }, 60),
        )
        listOf(1f, 1.5f).forEach { fontScale ->
            render(state, 1440, 900, fontScale, name = if (fontScale == 1f) "approved-workspace" else "approved-larger-text") { scene ->
                listOf("Slow 3G", "400 kbps", "400 ms", "Configured limit 400 kbps", "Download 12 kbps", "Upload 385 kbps",
                    "Active flows", "11", "Queued", "53 B", "Downloaded", "977 KB", "Uploaded", "3 MB",
                    "Overview", "Rules", "Profiles").forEach { label -> assertVisible(scene, label, 900) }
                assertFalse(scene.nodes().any { it.text().contains("Packet conditions:") || it.text().contains("Android UDP experimental") })
                assertFalse(scene.nodes().any { it.text() == "Add rule" })
            }
            render(state.copy(activeTab = NetworkConditionsTab.RULES), 1440, 900, fontScale,
                name = if (fontScale == 1f) "empty-rules" else "empty-rules-larger-text") { scene ->
                assertVisible(scene, "No condition rules", 900)
                assertEquals(1, scene.nodes().count { it.text() == "Add rule" })
                assertFalse(scene.nodes().any { it.text() == "Actual throughput" })
            }
        }
    }

    @Test
    fun `three tabs retain shared controls and restore rule scrolling with full height tables`() = onUiThread {
        listOf(Triple(1440, 900, 1f), Triple(976, 560, 1.25f)).forEach { (width, height, fontScale) ->
            val selected = mutableStateOf(stateWithRules(1000).copy(activeTab = NetworkConditionsTab.OVERVIEW))
            ImageComposeScene(width, height, Density(1f, fontScale), content = {
                KNetTheme { Workspace(selected.value, onTab = { selected.value = selected.value.copy(activeTab = it) }) }
            }).use { scene ->
                scene.frame()
                assertVisible(scene, "Actual throughput", height)
                assertTrue(scene.nodes().any { it.text() == "Mixed profiles" })
                scene.save("three-tabs-overview-$width")
                scene.clickText("Rules"); scene.frame()
                assertVisible(scene, "Add rule", height)
                assertFalse(scene.nodes().any { it.text() == "Actual throughput" })
                assertTrue(scene.scrollableTableHeight() > height / 2f)
                val scrollToIndex = scene.nodes().firstNotNullOf { it.config.getOrNull(SemanticsActions.ScrollToIndex)?.action }
                assertTrue(scrollToIndex(80))
                scene.frame()
                assertVisible(scene, "api80.example.test:443", height)
                scene.save("three-tabs-rules-$width")
                scene.clickText("Overview"); scene.frame()
                assertVisible(scene, "Actual throughput", height)
                scene.clickText("Rules"); scene.frame()
                assertVisible(scene, "api80.example.test:443", height)
                scene.clickText("Profiles"); scene.frame()
                assertVisible(scene, "New custom profile", height)
                assertFalse(scene.nodes().any { it.text() == "Actual throughput" || it.text() == "Add rule" })
                assertTrue(scene.scrollableTableHeight() > height / 2f)
                listOf("Reset", "Enabled", "Streaming 100 kbps").forEach { assertVisible(scene, it, height) }
                scene.save("three-tabs-profiles-$width")
            }
        }
    }

    @Test
    fun `global selector merges saved unlimited states without rewriting them and retains rule exceptions`() = onUiThread {
        for (savedId in listOf(null, NetworkConditionBuiltIns.NO_THROTTLING.id, NetworkConditionBuiltIns.STREAMING_100_KBPS.id)) {
            val rule = NetworkConditionRule(NetworkConditionRuleId("unlimited-exception"),
                NetworkConditionTarget.parse("unlimited.example.test", 443), NetworkConditionBuiltIns.NO_THROTTLING.id)
            val selected = mutableStateOf(NetworkConditionsState(configuration = NetworkConditionConfiguration(
                enabled = true, globalProfileId = savedId, rules = listOf(rule)), activeTab = NetworkConditionsTab.RULES))
            val changes = mutableListOf<NetworkConditionProfileId?>()
            ImageComposeScene(1440, 900, content = {
                KNetTheme { Workspace(selected.value, onProfile = {
                    changes += it
                    selected.value = selected.value.copy(configuration = selected.value.configuration.copy(globalProfileId = it))
                }) }
            }).use { scene ->
                scene.frame()
                assertTrue(scene.nodes().any { it.text() == "No throttling" }, "Rule exceptions keep their existing name")
                val label = if (savedId == NetworkConditionBuiltIns.STREAMING_100_KBPS.id) "Streaming 100 kbps" else NetworkConditionBuiltIns.NO_THROTTLING.name
                scene.clickText(label); scene.frame()
                val options = scene.nodes().filter { it.config.getOrNull(SemanticsProperties.Selected) != null }
                assertEquals(1, options.count { it.text() == NetworkConditionBuiltIns.NO_THROTTLING.name })
                assertFalse(options.any { it.text() == "No global throttling" || it.text() == "Domain rules only" || it.text() == "Lossy / unstable" })
                assertTrue(options.any { it.text() == "Unstable connection" })
                assertTrue(changes.isEmpty(), "Rendering a saved alias must not change persisted settings")
                assertEquals(savedId, selected.value.configuration.globalProfileId)
                val choice = options.single { it.text() == NetworkConditionBuiltIns.NO_THROTTLING.name }
                assertTrue(assertNotNull(choice.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
                scene.frame()
                assertEquals(listOf<NetworkConditionProfileId?>(null), changes)
                assertTrue(selected.value.configuration.enabled)
                assertEquals(listOf(rule), selected.value.configuration.rules)
                scene.save("global-preset-cleanup-${savedId?.value ?: "unset"}")
            }
        }
    }

    @Test
    fun `live graph shows measured rates separately from the configured reference`() = onUiThread {
        val state = NetworkConditionsState(
            configuration = NetworkConditionConfiguration(enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id),
            throughput = NetworkThroughputHistory(LongArray(60) { 95_000 }, LongArray(60) { 7_000 },
                BooleanArray(60) { true }, 60),
        )
        render(state, 1440, 900, name = "live-throughput") { scene ->
            assertTrue(scene.nodes().any { it.text() == "Download 95 kbps" })
            assertTrue(scene.nodes().any { it.text() == "Upload 7 kbps" })
            assertTrue(scene.nodes().any { it.text().contains("Download reference 100 kbps") })
            assertFalse(scene.nodes().any { it.text() == "Waiting for traffic" })
        }
    }

    @Test
    fun `rule editor keeps save and cancel visible while protocol fields overflow`() = onUiThread {
        val definition = NetworkConditionProtocolDefinition(
            NetworkConditionProtocolId.WEBSOCKET, "WebSocket", 1, NetworkConditionInterceptionUnit.PROTOCOL_MESSAGE,
            fields = List(8) { index -> ProtocolCriteriaFieldDefinition.Text(
                ProtocolCriteriaFieldId("field$index"), "Protocol field $index",
                description = "An optional selector supplied by the protocol extension.",
            ) },
        )
        val draft = NetworkConditionRuleDraft("api.example.test", "", null,
            NetworkConditionBuiltIns.NO_THROTTLING.id, protocolId = NetworkConditionProtocolId.WEBSOCKET)
        ImageComposeScene(480, 420, Density(1f, 1.25f), content = {
            KNetTheme {
                NetworkConditionRuleEditor(draft, NetworkConditionBuiltIns.all, listOf(definition),
                    {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
            }
        }).use { scene ->
            scene.frame()
            assertVisible(scene, "Save rule", 420)
            assertVisible(scene, "Cancel", 420)
            assertTrue(scene.nodes().any { it.config.getOrNull(SemanticsActions.ScrollBy) != null })
            scene.save("rule-editor-overflow")
        }
    }

    @Test
    fun `selecting HTTP renders text and choice fields and retains the selected path mode`() = onUiThread {
        val pathModeId = ProtocolCriteriaFieldId("path-mode")
        // Exercise the extension contract without importing engine implementations into desktop presentation.
        val http = NetworkConditionProtocolDefinition(
            NetworkConditionProtocolId.HTTP, "HTTP", 1, NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
            fields = listOf(
                ProtocolCriteriaFieldDefinition.Text(ProtocolCriteriaFieldId("method"), "Request Method"),
                ProtocolCriteriaFieldDefinition.Text(ProtocolCriteriaFieldId("path"), "Request Path"),
                ProtocolCriteriaFieldDefinition.Choice(pathModeId, "Path Match", options = listOf(
                    ProtocolCriteriaOption("exact", "Exact path"), ProtocolCriteriaOption("prefix", "Path prefix"),
                ), defaultValue = "exact"),
            ),
        )
        val definitions = NetworkConditionProtocolRegistry().definitions + http
        val draft = mutableStateOf(NetworkConditionRuleDraft("api.example.test", "", null,
            NetworkConditionBuiltIns.NO_THROTTLING.id))
        ImageComposeScene(640, 720, content = {
            KNetTheme {
                NetworkConditionRuleEditor(draft.value, NetworkConditionBuiltIns.all, definitions,
                    {}, {}, {}, {}, {}, { protocolId ->
                        draft.value = draft.value.copy(protocolId = protocolId, protocolValues =
                            definitions.first { it.protocolId == protocolId }.fields.filterIsInstance<ProtocolCriteriaFieldDefinition.Choice>()
                                .map { ProtocolCriteriaValue(it.id, it.defaultValue) })
                    }, { fieldId, value ->
                        draft.value = draft.value.copy(protocolValues = draft.value.protocolValues
                            .filterNot { it.fieldId == fieldId } + ProtocolCriteriaValue(fieldId, value))
                    }, {}, {})
            }
        }).use { scene ->
            scene.frame()
            assertFalse(scene.nodes().any { it.text() == "Path Match" })
            scene.clickText("All traffic"); scene.frame()
            scene.clickText("HTTP"); scene.frame()
            assertEquals(NetworkConditionProtocolId.HTTP, draft.value.protocolId)
            listOf("Request Method", "Request Path", "Path Match", "Exact path").forEach { label ->
                assertVisible(scene, label, 720)
            }
            scene.clickText("Exact path"); scene.frame()
            scene.clickText("Path prefix"); scene.frame()
            assertEquals("prefix", draft.value.protocolValues.single { it.fieldId == pathModeId }.value)
            assertVisible(scene, "Path prefix", 720)
            assertVisible(scene, "Save rule", 720)
            scene.save("rule-editor-http")
        }
    }

    private fun stateWithRules(count: Int): NetworkConditionsState {
        val rules = List(count) { index ->
            NetworkConditionRule(NetworkConditionRuleId("rule$index"),
                NetworkConditionTarget.parse("api$index.example.test", 443), NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
        }
        return NetworkConditionsState(
            configuration = NetworkConditionConfiguration(enabled = true,
                globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id, rules = rules),
            ruleDetails = rules.associate { it.id to NetworkConditionRuleDetails("GraphQL", "Operation name: GetSubscriptions · Operation type: Query") },
            activeTab = NetworkConditionsTab.RULES,
        )
    }

    @androidx.compose.runtime.Composable
    private fun Workspace(state: NetworkConditionsState, onTab: (NetworkConditionsTab) -> Unit = {},
        onProfile: (NetworkConditionProfileId?) -> Unit = {}) {
        NetworkConditionsWorkspace(state, {}, {}, onProfile, onTab, {}, { _, _ -> }, {}, {}, {}, {}, {})
    }

    private fun render(state: NetworkConditionsState, width: Int, height: Int, fontScale: Float = 1f,
        name: String, assertions: (ImageComposeScene) -> Unit) {
        ImageComposeScene(width, height, Density(1f, fontScale), content = {
            KNetTheme { Workspace(state) }
        }).use { scene ->
            scene.frame()
            scene.save(name)
            assertions(scene)
        }
    }

    private fun ImageComposeScene.frame() {
        repeat(2) {
            frameNanos += 1_000_000_000L
            render(frameNanos).close()
        }
    }

    private fun ImageComposeScene.clickText(text: String) {
        val node = nodes().first { it.text() == text && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
        val position = node.boundsInWindow.center
        sendPointerEvent(PointerEventType.Move, position)
        sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, buttons = PointerButtons(), button = PointerButton.Primary)
    }

    private fun ImageComposeScene.save(name: String) {
        val directory = File("build/reports/network-conditions-ui").apply { mkdirs() }
        frameNanos += 1_000_000_000L
        render(frameNanos).use { image ->
            image.encodeToData()?.use { data -> File(directory, "$name.png").writeBytes(data.bytes) }
        }
    }

    private fun assertVisible(scene: ImageComposeScene, text: String, height: Int) {
        assertTrue(scene.nodes().any {
            it.text().contains(text) && it.boundsInRoot.height > 0 && it.boundsInRoot.bottom <= height && it.boundsInRoot.top >= 0
        }, "Expected visible content: $text")
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        descendants(owner.rootSemanticsNode)
    }

    private fun ImageComposeScene.scrollableTableHeight(): Float =
        nodes().first { it.config.getOrNull(SemanticsActions.ScrollToIndex) != null }.boundsInRoot.height

    private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private fun onUiThread(block: () -> Unit) = EventQueue.invokeAndWait(block)
}
