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
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.KNetSegmentedButton
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsField
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState

/** Renders application appearance capabilities and typed scripting defaults. */
@Composable
fun AppearanceTab(
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
                text = "Appearance",
                style = typography.titleLarge.copy(color = colors.textPrimary),
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Review the active visual mode and configure defaults for newly authored requests.",
                style = typography.bodyMedium.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsItem(
            title = "Color Theme",
            description = "KNet currently follows the operating system theme automatically.",
            compact = compact,
            titleAccessory = {
                KNetBadge(
                    text = "UPCOMING",
                    containerColor = colors.semantic.infoContainer,
                    contentColor = colors.semantic.info,
                )
            },
        ) {
            KNetSegmentedButton(
                options = listOf("Dark", "Light", "System"),
                selectedIndex = 2,
                onOptionSelected = {},
                enabled = false,
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
