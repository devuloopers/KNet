package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.workspace.model.TimeoutUnit
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetSegmentedButton
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState

/**
 * Tab content view rendering Proxy Listening Port, Timeouts, OS Trust Store, and Data Directory settings.
 *
 * @param state Immutable settings screen state.
 * @param onIntent Action callback dispatching settings user intents.
 * @param onCopyPath Action callback copying the data directory path to clipboard.
 * @param modifier Composable layout modifier.
 */
@Composable
fun NetworkProxyTab(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onCopyPath: () -> Unit,
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
                text = "Network & Proxy",
                style = typography.titleLarge.copy(color = themeColors.textPrimary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Configure interception settings, ports, timeouts, and certificate trust stores.",
                style = typography.bodyMedium.copy(color = themeColors.textSecondary)
            )
        }

        // Card 1: Proxy Listening Port
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Proxy Listening Port",
                        style = typography.titleSmall.copy(color = themeColors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Port number for incoming proxy traffic interception.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary)
                    )
                }

                Box(modifier = Modifier.width(100.dp)) {
                    KNetTextField(
                        value = state.proxyPort,
                        onValueChange = { onIntent(SettingsIntent.UpdateProxyPort(it)) },
                        placeholder = "8080",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Card 2: Live Interception Timeout
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Interception Timeout",
                        style = typography.titleSmall.copy(color = themeColors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Maximum duration suspended traffic remains paused before auto-dropping.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.width(70.dp)) {
                        KNetTextField(
                            value = state.liveInterceptionTimeoutValue,
                            onValueChange = {
                                onIntent(SettingsIntent.UpdateLiveInterceptionTimeout(it, state.liveInterceptionTimeoutUnit))
                            },
                            placeholder = "60",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    KNetSegmentedButton(
                        options = listOf(TimeoutUnit.SECONDS.label, TimeoutUnit.MINUTES.label),
                        selectedIndex = if (state.liveInterceptionTimeoutUnit == TimeoutUnit.MINUTES) 1 else 0,
                        onOptionSelected = { index ->
                            val chosenUnit = if (index == 1) TimeoutUnit.MINUTES else TimeoutUnit.SECONDS
                            onIntent(SettingsIntent.UpdateLiveInterceptionTimeout(state.liveInterceptionTimeoutValue, chosenUnit))
                        }
                    )
                }
            }
        }

        // Card 3: API Studio Request Timeout
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "API Studio Request Timeout",
                        style = typography.titleSmall.copy(color = themeColors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Maximum execution duration allowed for API Studio client HTTP requests.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.width(70.dp)) {
                        KNetTextField(
                            value = state.apiStudioTimeoutValue,
                            onValueChange = {
                                onIntent(SettingsIntent.UpdateApiStudioTimeout(it, state.apiStudioTimeoutUnit))
                            },
                            placeholder = "60",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    KNetSegmentedButton(
                        options = listOf(TimeoutUnit.SECONDS.label, TimeoutUnit.MINUTES.label),
                        selectedIndex = if (state.apiStudioTimeoutUnit == TimeoutUnit.MINUTES) 1 else 0,
                        onOptionSelected = { index ->
                            val chosenUnit = if (index == 1) TimeoutUnit.MINUTES else TimeoutUnit.SECONDS
                            onIntent(SettingsIntent.UpdateApiStudioTimeout(state.apiStudioTimeoutValue, chosenUnit))
                        }
                    )
                }
            }
        }

        // Card 4: Operating System Trust Store
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Operating System Trust Store",
                            style = typography.titleSmall.copy(color = themeColors.textPrimary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        StatusBadge(isTrusted = state.isCaTrusted)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Root CA certificate used for HTTPS traffic decryption.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary)
                    )
                }

                KNetButton(
                    onClick = { onIntent(SettingsIntent.InstallRootCa) },
                    enabled = !state.isInstallingCa,
                    variant = ButtonVariant.Secondary
                ) {
                    Text(if (state.isInstallingCa) "Installing..." else "Install Root CA into OS")
                }
            }
        }

        // Card 5: Data Directory
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Data Directory",
                            style = typography.titleSmall.copy(color = themeColors.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Location where application data and configs are stored.",
                            style = typography.bodySmall.copy(color = themeColors.textSecondary)
                        )
                    }

                    KNetButton(
                        onClick = { onIntent(SettingsIntent.OpenDataDirectory) },
                        variant = ButtonVariant.Secondary
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = themeColors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Directory")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeColors.background, RoundedCornerShape(4.dp))
                        .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.dataDirectory,
                        style = typography.bodyMedium.copy(
                            color = themeColors.textPrimary,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCopyPath,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Path",
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
