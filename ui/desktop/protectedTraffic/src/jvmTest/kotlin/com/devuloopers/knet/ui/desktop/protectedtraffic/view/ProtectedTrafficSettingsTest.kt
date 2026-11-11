package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Renders the actual policy settings screen with the actual ViewModel/use-case boundary. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ProtectedTrafficSettingsTest {
    @Test fun `settings directly expose policy and rules without observed connections or presets`() = EventQueue.invokeAndWait {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val repository = Repository()
            val viewModel = ProtectedTrafficViewModel(
                ObserveProtectedTrafficConfigurationUseCase(repository), SetProtectedTrafficDefaultActionUseCase(repository),
                UpsertProtectedTrafficRuleUseCase(repository), DeleteProtectedTrafficRuleUseCase(repository),
            )
            store.put("protected", viewModel)
            ImageComposeScene(1280, 900, content = { KNetTheme { ProtectedTrafficScreen(viewModel) } }).use { scene ->
                var time = 0L
                fun frame() { repeat(4) { time += 1_000_000_000; scene.render(time).close() } }
                fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                fun click(text: String) {
                    val node = nodes().first { it.config.getOrNull(SemanticsActions.OnClick) != null &&
                        descendants(it).any { child -> child.text() == text } }
                    assertTrue(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
                    frame()
                }
                frame()
                val visible = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                listOf("Default TLS connection handling", "Manual rules").forEach {
                    assertTrue(it in visible, "Missing settings content: $it")
                }
                assertFalse(visible.any { it in listOf("Compatibility presets", "Google Play Billing", "Google system services", "Apple App Store and StoreKit", "Smart Compatibility") })
                assertTrue(repository.configuration.value.enabledBuiltInGroups.isEmpty())
                assertFalse(nodes().any { it.text() == "Bypass" && it.config.getOrNull(SemanticsActions.OnClick) != null })
                click("Tunnel")
                assertEquals(ProtectedTrafficAction.TUNNEL, repository.configuration.value.defaultAction)
                click("Inspect")
                val directory = File("build/reports/protected-traffic-ui").apply { mkdirs() }
                scene.render(++time).use { picture -> picture.encodeToData()?.use { File(directory, "settings.png").writeBytes(it.bytes) } }
                val imported = ProtectedTrafficRule(ProtectedTrafficRuleId("legacy-visible"), action = ProtectedTrafficAction.TUNNEL,
                    destination = ProtectedDestinationSelector.parse("legacy.example.com"), origin = ProtectedTrafficRuleOrigin.MIGRATED_PRESET)
                repository.configuration.value = repository.configuration.value.copy(rules = listOf(imported))
                frame()
                assertTrue(nodes().any { it.text().contains("legacy.example.com") })
                assertTrue(nodes().any { it.text().contains("IMPORTED") })
                viewModel.setRuleEnabled(imported, false)
                frame()
                assertFalse(repository.configuration.value.rules.single().enabled)
                viewModel.deleteRule(imported.id)
                frame()
                assertTrue(repository.configuration.value.rules.isEmpty())
                assertFalse(nodes().any { it.text() in listOf("Observed connections", "Overview", "Advanced", "Decision details") })
                click("Add rule")
                assertNotNull(viewModel.state.value.ruleDraft)
                click("Cancel")
                assertNull(viewModel.state.value.ruleDraft)
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
    private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private class Repository : ProtectedTrafficRepository {
        override val configuration = MutableStateFlow(ProtectedTrafficConfiguration())
        override suspend fun setDefaultAction(action: ProtectedTrafficAction) { configuration.value = configuration.value.copy(defaultAction = action) }
        override suspend fun upsertRule(rule: ProtectedTrafficRule) { configuration.value = configuration.value.copy(rules = configuration.value.rules.filterNot { it.id == rule.id } + rule) }
        override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) { configuration.value = configuration.value.copy(rules = configuration.value.rules.filterNot { it.id == ruleId }) }

    }

}
