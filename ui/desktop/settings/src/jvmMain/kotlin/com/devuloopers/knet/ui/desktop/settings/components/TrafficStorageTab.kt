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
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.dropdown.KNetSearchableDropdown
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsField
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState

/** Renders startup traffic policy and truthful payload-retention capability settings. */
@Composable
fun TrafficStorageTab(
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
                text = "Traffic & Storage",
                style = typography.titleLarge.copy(color = colors.textPrimary),
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Control stored traffic startup behavior and inspect payload-retention capabilities.",
                style = typography.bodyMedium.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsItem(
            title = "Auto-Clear Traffic on Startup",
            description = "Clears stored traffic once during the next KNet application launch.",
            compact = compact,
        ) {
            KNetSwitch(
                checked = state.autoClearTrafficOnStartup,
                onCheckedChange = { onIntent(SettingsIntent.ToggleAutoClearTraffic(it)) },
                enabled = !state.isLoading && SettingsField.AUTO_CLEAR_TRAFFIC !in state.savingFields,
            )
        }

        SettingsItem(
            title = "Payload Size Cache Limit",
            description = "A configurable body-retention policy is not connected to traffic capture yet.",
            compact = compact,
            titleAccessory = {
                KNetBadge(
                    text = "UPCOMING",
                    containerColor = colors.semantic.infoContainer,
                    contentColor = colors.semantic.info,
                )
            },
        ) {
            KNetSearchableDropdown(
                items = listOf("${state.payloadCacheLimitMb} MB"),
                selectedItem = "${state.payloadCacheLimitMb} MB",
                onItemSelected = {},
                enabled = false,
                itemText = { it },
            )
        }
    }
}
