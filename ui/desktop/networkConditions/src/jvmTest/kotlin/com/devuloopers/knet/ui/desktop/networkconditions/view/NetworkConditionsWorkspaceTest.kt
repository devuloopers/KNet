package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.runtime.mutableStateOf
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
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
    fun `minimum window with larger text retains controls first rule and packet qualification`() = onUiThread {
        render(stateWithRules(1000), 976, 560, fontScale = 1.25f, name = "compact-rules") { scene ->
            assertVisible(scene, "Add rule", 560)
            assertVisible(scene, "api0.example.test:443", 560)
            assertVisible(scene, "Packet conditions:", 560)
            assertTrue(scene.nodes().count { it.text().contains("api") && it.text().contains("example.test") } < 40,
                "The table must compose only visible rows, not the entire rule list")
            assertTrue(scene.nodes().any { it.text().contains("Mixed profiles") })
            assertFalse(scene.nodes().any { it.text().contains("Download reference") })
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
    fun `empty bypassed workspace shows waiting state and add action without sample traffic`() = onUiThread {
        render(NetworkConditionsState(), 1280, 800, name = "empty-bypassed") { scene ->
            assertVisible(scene, "Add rule", 800)
            assertVisible(scene, "No condition rules", 800)
            assertTrue(scene.nodes().any { it.text().contains("Waiting for traffic") })
            assertTrue(scene.nodes().any { it.text().contains("Conditions bypassed") })
        }
    }

    @Test
    fun `global dropdown keeps domain rules only available`() = onUiThread {
        render(stateWithRules(3), 1440, 900, name = "rules") { scene ->
            val dropdown = scene.nodes().first { it.text() == "Streaming 100 kbps" && it.config.getOrNull(SemanticsActions.OnClick) != null }
            assertTrue(assertNotNull(dropdown.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
            scene.frame()
            assertTrue(scene.nodes().any { it.text() == "Domain rules only" })
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
        )
    }

    @androidx.compose.runtime.Composable
    private fun Workspace(state: NetworkConditionsState, onTab: (NetworkConditionsTab) -> Unit = {}) {
        NetworkConditionsWorkspace(state, {}, {}, {}, onTab, {}, { _, _ -> }, {}, {}, {}, {}, {})
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

    private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private fun onUiThread(block: () -> Unit) = EventQueue.invokeAndWait(block)
}
