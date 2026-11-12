@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.devuloopers.knet.ui.desktop.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.ui.core.foundation.color.KNetDarkColors
import com.devuloopers.knet.ui.core.foundation.theme.KNetApplicationTheme
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.*
import com.devuloopers.knet.ui.desktop.settings.view.SettingsScreenContent
import java.awt.EventQueue
import java.io.File
import org.jetbrains.skiko.SystemTheme
import kotlin.test.*

/** Real Settings rendering verifies host-theme isolation, removed appearance controls and working scripting choices. */
@OptIn(ExperimentalComposeUiApi::class)
class FixedDarkSettingsTest {
    @Test fun `wide and compact settings stay dark across host appearance changes and preserve scripting selection`() = EventQueue.invokeAndWait {
        for (width in listOf(1400, 680)) {
            val systemTheme = mutableStateOf(SystemTheme.LIGHT)
            val state = mutableStateOf(SettingsState(activeTab = SettingsTab.SCRIPTING, isLoading = false))
            val intents = mutableListOf<SettingsIntent>()
            var observedHostDark = true
            var darkContent = false
            var frameNanos = 0L
            ImageComposeScene(width, 860, content = {
                // The pinned Compose local is used only in this headless test; the actual OS is never changed.
                CompositionLocalProvider(LocalSystemTheme provides systemTheme.value) {
                    observedHostDark = isSystemInDarkTheme()
                    KNetApplicationTheme {
                        darkContent = KNetTheme.colors == KNetDarkColors && MaterialTheme.colorScheme.background == KNetDarkColors.background
                        SettingsScreenContent(state.value, onIntent = { intent ->
                            intents += intent
                            if (intent is SettingsIntent.SetScriptLanguage) state.value = state.value.copy(scriptLanguage = intent.language)
                        })
                    }
                }
            }).use { scene ->
                fun frame() { repeat(4) { frameNanos += 1_000_000_000L; scene.render(frameNanos).close() } }
                fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                fun labels() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                fun activate(label: String) {
                    val node = nodes().first { candidate -> candidate.config.getOrNull(SemanticsActions.OnClick) != null &&
                        candidate.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }
                    assertTrue(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
                    frame()
                }
                frame()
                assertFalse(observedHostDark)
                assertTrue(darkContent)
                assertTrue("Scripting" in labels())
                assertTrue("Default Scripting Language" in labels())
                assertFalse(labels().any { it in setOf("Appearance", "Color Theme", "Dark", "Light", "System") })
                activate("JavaScript")
                activate("Kotlin")
                assertEquals(SettingsIntent.SetScriptLanguage(ScriptLanguage.KOTLIN), intents.last())
                systemTheme.value = SystemTheme.DARK
                frame()
                assertTrue(observedHostDark)
                assertTrue(darkContent)
                assertTrue("Kotlin" in labels())
                systemTheme.value = SystemTheme.LIGHT
                frame()
                assertFalse(observedHostDark)
                assertTrue(darkContent)
                val directory = File("build/reports/dark-appearance").apply { mkdirs() }
                scene.render(++frameNanos).use { image -> image.encodeToData()?.use { File(directory, "settings-$width.png").writeBytes(it.bytes) } }
            }
        }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
}
