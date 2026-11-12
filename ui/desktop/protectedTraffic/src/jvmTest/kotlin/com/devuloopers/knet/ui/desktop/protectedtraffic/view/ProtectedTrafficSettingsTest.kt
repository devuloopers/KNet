package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetApplicationTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Real manager, ViewModel and use cases exercise saved rules without reintroducing automatic compatibility. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ProtectedTrafficSettingsTest {
    @Test fun `default TLS help renders accurate fallback copy inside the manager window`() {
        val clock = TestCoroutineScheduler()
        fixture(clock = clock) { ui ->
            ui.frame()
            val anchor = ui.tag("Default TLS action")
            ui.scene.sendPointerEvent(PointerEventType.Move, anchor.boundsInWindow.center)
            ui.frame(); clock.advanceTimeBy(500); clock.runCurrent(); ui.frame()
            val tooltip = ui.nodes().first { it.text().contains("The failed connection is not retried by KNet") }
            assertTrue(tooltip.boundsInWindow.left >= 0 && tooltip.boundsInWindow.right <= 1280)
            assertTrue(tooltip.boundsInWindow.top >= 0 && tooltip.boundsInWindow.bottom <= 900)
            ui.save("manager-tls-handling-help")
        }
    }

    @Test fun `TLS choices expose accurate accessible explanations for selected and available modes`() = fixture { ui ->
        ui.frame()
        fun explanation(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        fun selectedExplanation() = ui.descendants(ui.tag("Default TLS action")).map(::explanation).first { it.isNotBlank() }
        val initial = selectedExplanation()
        assertTrue(initial.contains("client explicitly rejects"))
        assertTrue(initial.contains("eligible later connections temporarily tunnel"))
        assertTrue(initial.contains("not retried by KNet; the app must reconnect"))
        ui.activateTag("Default TLS action")
        val expected = mapOf("Inspect with automatic fallback" to "not retried by KNet",
            "Inspect only" to "automatic tunneling is disabled", "Tunnel" to "HTTP headers and bodies remain encrypted",
            "Block" to "Reject TLS connections")
        for ((label, behavior) in expected) {
            val description = explanation(ui.action(label))
            assertTrue(description.contains(behavior), "Missing behavior for $label")
            assertTrue(description.contains("Saved rules take priority. Changes apply to new connections."))
        }
        ui.activate("Tunnel")
        assertTrue(selectedExplanation().contains("Forward TLS traffic without decrypting it"))
    }

    @Test fun `manager searches live saved rules and supports default action toggle delete and an animated editor`() = fixture { ui ->
        ui.frame()
        for (label in listOf("Protected Traffic", "Default TLS handling", "Enabled", "Destination", "Application", "Port", "Transport", "Action", "Actions"))
            assertTrue(label in ui.labels(), "Missing manager content: $label")
        assertFalse(ui.labels().any { it in listOf("Smart Compatibility", "Observed connections", "Compatibility presets", "Overview", "Advanced", "Decision details") })
        assertTrue("Inspect with automatic fallback" in ui.labels())
        ui.activateTag("Default TLS action")
        for (label in listOf("Inspect with automatic fallback", "Inspect only", "Tunnel", "Block")) assertTrue(label in ui.labels())
        ui.activate("Tunnel")
        assertEquals(ProtectedTrafficAction.TUNNEL, ui.repository.configuration.value.defaultAction)
        ui.activateTag("Default TLS action"); ui.activate("Inspect only")
        assertFalse(ui.repository.configuration.value.automaticTlsFallbackEnabled)
        ui.activateTag("Default TLS action"); ui.activate("Inspect with automatic fallback")
        assertTrue(ui.repository.configuration.value.automaticTlsFallbackEnabled)
        val imported = rule("imported", "legacy.example.com").copy(origin = ProtectedTrafficRuleOrigin.MIGRATED_PRESET)
        ui.repository.addExternalRule(imported)
        ui.frame()
        assertTrue("legacy.example.com" in ui.labels())
        assertTrue("IMPORTED" in ui.labels())
        ui.activateTag("Enable rule imported")
        assertFalse(ui.repository.configuration.value.rules.single().enabled)
        ui.activateDescription("Edit rule imported")
        assertTrue("Edit protected traffic rule" in ui.labels())
        assertTrue(ui.labels().any { it.startsWith("Imported exception. Changing") })
        assertTrue(ui.tag("Protected rule editor").boundsInRoot.right <= 1280)
        ui.activate("Cancel")
        assertNull(ui.viewModel.state.value.ruleDraft)
        ui.activateDescription("Delete rule imported")
        assertTrue(ui.repository.configuration.value.rules.isEmpty())
        ui.activate("Add rule")
        assertNotNull(ui.viewModel.state.value.ruleDraft)
        ui.activateDescription("Close protected rule editor")
        assertNull(ui.viewModel.state.value.ruleDraft)
        ui.save("manager-empty")
    }

    @Test fun `drawer validates in place and edits the same disabled rule without narrowing wildcard selectors`() = fixture { ui ->
        ui.frame(); ui.activate("Add rule")
        ui.setText("Protected rule Destination", "*.invalid")
        ui.activate("Save rule")
        assertNotNull(ui.viewModel.state.value.draftError)
        assertNotNull(ui.viewModel.state.value.ruleDraft)
        assertTrue(ui.nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "Protected rule validation" })
        ui.setText("Protected rule Destination", "*.Video.Example.Test.")
        assertNull(ui.viewModel.state.value.draftError)
        ui.setText("Protected rule Port", "70000")
        assertNotNull(ui.action("Save rule").config.getOrNull(SemanticsProperties.Disabled))
        ui.setText("Protected rule Port", "443")
        ui.activateTag("Rule action"); ui.activate("Block")
        ui.activate("Save rule")
        val saved = ui.repository.configuration.value.rules.single()
        assertEquals(ProtectedDestinationSelector.parse("*.video.example.test"), saved.destination)
        assertEquals(ProtectedTrafficAction.BLOCK, saved.action)
        ui.activateTag("Enable rule ${saved.id.value}")
        ui.activateDescription("Edit rule ${saved.id.value}")
        ui.activateTag("Rule transport"); ui.activate("All transports")
        ui.setText("Protected rule Port", "")
        ui.activateTag("Rule IP family"); ui.activate("IPV6")
        ui.save("manager-editor")
        ui.activate("Save rule")
        val edited = ui.repository.configuration.value.rules.single()
        assertEquals(saved.id, edited.id)
        assertFalse(edited.enabled)
        assertNull(edited.transport); assertNull(edited.port)
        assertEquals(ProtectedIpFamily.IPV6, edited.ipFamily)
        assertTrue("All · IPV6" in ui.labels())
        ui.activate("Add rule"); ui.activate("Cancel")
        assertEquals(1, ui.repository.configuration.value.rules.size)
    }

    @Test fun `search retains focus supports literal matching and observes rules saved elsewhere`() = fixture { ui ->
        ui.repository.addExternalRule(rule("video", "video.example.com"))
        ui.repository.addExternalRule(rule("store", "store.example.com").copy(action = ProtectedTrafficAction.BLOCK))
        ui.frame()
        val editable = ui.editable("Protected traffic search")
        editable.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
        ui.frame()
        ui.setText("Protected traffic search", "v")
        assertTrue(ui.descendants(ui.tag("Protected traffic search")).any { it.config.getOrNull(SemanticsProperties.Focused) == true })
        ui.setText("Protected traffic search", "VIDEO 443 tunnel")
        assertTrue("video.example.com" in ui.labels()); assertFalse("store.example.com" in ui.labels())
        ui.repository.addExternalRule(rule("external", "video.remote.example.com"))
        ui.frame()
        assertTrue("video.remote.example.com" in ui.labels())
        ui.setText("Protected traffic search", "[no match]")
        assertTrue("No matching rules" in ui.labels())
        ui.activate("Clear search")
        assertEquals("", ui.viewModel.state.value.searchQuery)
        assertTrue("store.example.com" in ui.labels())
        ui.save("manager-rules")
    }

    @Test fun `compact larger text manager keeps table scrollbars and drawer actions accessible`() = fixture(680, 560, 1.25f) { ui ->
        repeat(60) { ui.repository.addExternalRule(rule("row-$it", "long-api-host-$it.example.com")) }
        ui.frame()
        val tableNodes = ui.descendants(ui.tag("Protected rules table"))
        assertTrue(tableNodes.any { it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.maxValue?.invoke()?.let { value -> value > 0 } == true })
        assertTrue(tableNodes.any { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke()?.let { value -> value > 0 } == true })
        assertTrue(tableNodes.count { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("Protected rule row-") == true } < 60)
        ui.setText("Protected traffic search", "no match")
        val emptyLabel = ui.nodes().first { it.text() == "No matching rules" }
        assertTrue(emptyLabel.boundsInRoot.left >= 0 && emptyLabel.boundsInRoot.right <= 680)
        ui.activate("Clear search")
        ui.save("manager-compact")
        ui.activate("Add rule")
        val editor = ui.tag("Protected rule editor")
        assertTrue(editor.boundsInRoot.left >= 0 && editor.boundsInRoot.right <= 680)
        for (label in listOf("Save rule", "Cancel")) {
            val button = ui.action(label)
            assertTrue(button.boundsInRoot.bottom <= 560 && button.boundsInRoot.left >= 0)
        }
        assertTrue(ui.descendants(editor).any { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null })
        ui.save("manager-editor-compact")
    }

    private fun rule(id: String, host: String) = ProtectedTrafficRule(ProtectedTrafficRuleId(id),
        action = ProtectedTrafficAction.TUNNEL, destination = ProtectedDestinationSelector.parse(host), port = 443,
        transport = ProtectedTransportProtocol.TCP)

    private fun fixture(width: Int = 1280, height: Int = 900, fontScale: Float = 1f, clock: TestCoroutineScheduler? = null,
        block: (Ui) -> Unit) = EventQueue.invokeAndWait {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val repository = Repository()
            val viewModel = ProtectedTrafficViewModel(ObserveProtectedTrafficConfigurationUseCase(repository),
                SetProtectedTrafficDefaultActionUseCase(repository), UpsertProtectedTrafficRuleUseCase(repository), DeleteProtectedTrafficRuleUseCase(repository))
            store.put("protected", viewModel)
            ImageComposeScene(width, height, Density(1f, fontScale),
                coroutineContext = clock?.let { UnconfinedTestDispatcher(it) } ?: Dispatchers.Unconfined,
                content = { KNetApplicationTheme { ProtectedTrafficScreen(viewModel) } })
                .use { scene -> block(Ui(repository, viewModel, scene)) }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private class Ui(val repository: Repository, val viewModel: ProtectedTrafficViewModel, val scene: ImageComposeScene) {
        private var time = 0L
        fun frame() { repeat(5) { time += 1_000_000_000L; scene.render(time).close() } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun labels() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        fun tag(tag: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun editable(tag: String) = descendants(tag(tag)).first { it.config.getOrNull(SemanticsActions.SetText) != null }
        fun action(text: String) = nodes().first { it.config.getOrNull(SemanticsActions.OnClick) != null &&
            descendants(it).any { child -> child.text() == text } }
        fun activate(text: String) { assertTrue(action(text).config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()); frame() }
        fun activateTag(tag: String) {
            val action = descendants(tag(tag)).first { it.config.getOrNull(SemanticsActions.OnClick) != null }
            assertTrue(action.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()); frame()
        }
        fun activateDescription(text: String) {
            val action = nodes().first { text in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() && it.config.getOrNull(SemanticsActions.OnClick) != null }
            assertTrue(action.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()); frame()
        }
        fun setText(tag: String, value: String) { assertTrue(editable(tag).config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString(value))); frame() }
        fun save(name: String) {
            val directory = File("build/reports/protected-traffic-ui").apply { mkdirs() }
            scene.render(++time).use { image -> image.encodeToData()?.use { File(directory, "$name.png").writeBytes(it.bytes) } }
        }
    }
    private class Repository : ProtectedTrafficRepository {
        override val configuration = MutableStateFlow(ProtectedTrafficConfiguration())
        override suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean) { configuration.value = configuration.value.copy(defaultAction = action, automaticTlsFallbackEnabled = automaticFallbackEnabled) }
        fun addExternalRule(rule: ProtectedTrafficRule) { configuration.value = configuration.value.copy(rules = configuration.value.rules.filterNot { it.id == rule.id } + rule) }
        override suspend fun upsertRule(rule: ProtectedTrafficRule) = addExternalRule(rule)
        override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) { configuration.value = configuration.value.copy(rules = configuration.value.rules.filterNot { it.id == ruleId }) }
    }
}

private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
