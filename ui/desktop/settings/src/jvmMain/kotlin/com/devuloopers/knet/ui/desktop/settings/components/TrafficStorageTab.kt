package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.dropdown.KNetSearchableDropdown
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState

/**
 * Tab content view rendering Auto-Clear Traffic and Payload Cache Limit settings.
 */
@Composable
fun TrafficStorageTab(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Traffic & Storage",
                style = typography.titleLarge.copy(color = themeColors.textPrimary),
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manage live traffic session flushing and payload memory limits.",
                style = typography.bodyMedium.copy(color = themeColors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Clear Traffic on Startup",
                        style = typography.titleSmall.copy(color = themeColors.textPrimary),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatically flushes previous traffic records when KNet launches.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = state.autoClearTrafficOnStartup,
                    onCheckedChange = { onIntent(SettingsIntent.ToggleAutoClearTraffic(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )
            }
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Payload Size Cache Limit",
                        style = typography.titleSmall.copy(color = themeColors.textPrimary),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Maximum response body payload size cached per transaction.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(modifier = Modifier.width(160.dp)) {
                    KNetSearchableDropdown(
                        items = listOf("2 MB", "10 MB", "50 MB", "100 MB"),
                        selectedItem = "${state.maxPayloadMb} MB",
                        onItemSelected = { selected ->
                            val mb = selected.replace(" MB", "").toIntOrNull() ?: 10
                            onIntent(SettingsIntent.SetMaxPayloadMb(mb))
                        },
                        itemText = { it }
                    )
                }
            }
        }
    }
}
