package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsField
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState

/** Renders typed scripting defaults for newly authored requests. */
@Composable
fun ScriptingTab(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(
                text = "Scripting",
                style = typography.titleLarge.copy(color = colors.textPrimary),
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Configure the default language for new pre-request and post-response scripts.",
                style = typography.bodyMedium.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsItem(
            title = "Default Scripting Language",
            description = "Default engine used for new pre-request and post-response scripts.",
            compact = compact,
        ) {
            KNetDropdown(
                items = ScriptLanguage.entries,
                selectedItem = state.scriptLanguage,
                onItemSelected = { onIntent(SettingsIntent.SetScriptLanguage(it)) },
                enabled = !state.isLoading && SettingsField.SCRIPT_LANGUAGE !in state.savingFields,
                itemText = { language -> language.displayName },
            )
        }
    }
}

private val ScriptLanguage.displayName: String
    get() = when (this) {
        ScriptLanguage.JAVASCRIPT -> "JavaScript"
        ScriptLanguage.KOTLIN -> "Kotlin"
    }
