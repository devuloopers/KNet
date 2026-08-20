package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetSegmentedButton
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.InputFieldState
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsField
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.TimeoutUnit

/** Renders validated proxy, timeout, certificate trust, and data-directory settings. */
@Composable
fun NetworkProxyTab(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val controlsEnabled = !state.isLoading

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(
                text = "Network & Proxy",
                style = typography.titleLarge.copy(color = colors.textPrimary),
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Configure the local proxy listener, interception deadlines, and desktop trust.",
                style = typography.bodyMedium.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsItem(
            title = "Proxy Listener Port",
            description = "Changing this value restarts an active proxy listener after you apply it.",
            compact = compact,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                KNetTextField(
                    value = state.proxyPort,
                    onValueChange = { onIntent(SettingsIntent.UpdateProxyPort(it)) },
                    config = InputFieldConfig(
                        placeholder = "8080",
                        supportingText = state.proxyPortError,
                    ),
                    state = InputFieldState(
                        enabled = controlsEnabled,
                        isError = state.proxyPortError != null,
                    ),
                    modifier = Modifier.width(116.dp),
                )
                ApplySettingButton(
                    field = SettingsField.PROXY_PORT,
                    state = state,
                    enabled = state.proxyPortError == null,
                    onClick = { onIntent(SettingsIntent.CommitProxyPort) },
                )
            }
        }

        SettingsItem(
            title = "Live Interception Timeout",
            description = "Maximum pause before an untouched request or response continues unchanged.",
            compact = compact,
        ) {
            TimeoutSettingControl(
                value = state.liveInterceptionTimeoutValue,
                unit = state.liveInterceptionTimeoutUnit,
                error = state.liveInterceptionTimeoutError,
                field = SettingsField.LIVE_INTERCEPTION_TIMEOUT,
                state = state,
                controlsEnabled = controlsEnabled,
                onValueChanged = { value, unit ->
                    onIntent(SettingsIntent.UpdateLiveInterceptionTimeout(value, unit))
                },
                onApply = { onIntent(SettingsIntent.CommitLiveInterceptionTimeout) },
            )
        }

        SettingsItem(
            title = "API Studio Request Timeout",
            description = "Maximum execution duration for API Studio network requests.",
            compact = compact,
        ) {
            TimeoutSettingControl(
                value = state.apiStudioTimeoutValue,
                unit = state.apiStudioTimeoutUnit,
                error = state.apiStudioTimeoutError,
                field = SettingsField.API_STUDIO_TIMEOUT,
                state = state,
                controlsEnabled = controlsEnabled,
                onValueChanged = { value, unit ->
                    onIntent(SettingsIntent.UpdateApiStudioTimeout(value, unit))
                },
                onApply = { onIntent(SettingsIntent.CommitApiStudioTimeout) },
            )
        }

        SettingsItem(
            title = "Operating System Trust Store",
            description = "Registers the KNet Root CA so desktop HTTPS clients can trust intercepted certificates.",
            compact = compact,
            titleAccessory = { StatusBadge(isTrusted = state.isCaTrusted) },
        ) {
            KNetButton(
                onClick = { onIntent(SettingsIntent.InstallRootCa) },
                enabled = controlsEnabled && !state.isCaTrusted,
                loading = state.isInstallingCa,
                variant = ButtonVariant.Secondary,
            ) {
                Text(if (state.isCaTrusted) "Root CA Trusted" else "Install Root CA")
            }
        }

        SettingsCard {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Data Directory",
                    style = typography.titleSmall.copy(color = colors.textPrimary),
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "KNet runtime data, certificates, bodies, and database files are stored here.",
                    style = typography.bodySmall.copy(color = colors.textSecondary),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KNetTextField(
                        value = state.dataDirectory,
                        onValueChange = {},
                        state = InputFieldState(readOnly = true),
                        modifier = Modifier.weight(1f),
                    )
                    KNetCopyButton(
                        textToCopy = state.dataDirectory,
                        copiedText = "Copied",
                        contentDescription = "Copy data directory",
                    )
                    KNetButton(
                        onClick = { onIntent(SettingsIntent.OpenDataDirectory) },
                        enabled = controlsEnabled,
                        variant = ButtonVariant.Secondary,
                    ) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeoutSettingControl(
    value: String,
    unit: TimeoutUnit,
    error: String?,
    field: SettingsField,
    state: SettingsState,
    controlsEnabled: Boolean,
    onValueChanged: (String, TimeoutUnit) -> Unit,
    onApply: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KNetTextField(
            value = value,
            onValueChange = { onValueChanged(it, unit) },
            config = InputFieldConfig(
                placeholder = "60",
                supportingText = error,
            ),
            state = InputFieldState(enabled = controlsEnabled, isError = error != null),
            modifier = Modifier.width(90.dp),
        )
        KNetSegmentedButton(
            options = TimeoutUnit.entries.map(TimeoutUnit::label),
            selectedIndex = TimeoutUnit.entries.indexOf(unit),
            onOptionSelected = { index -> onValueChanged(value, TimeoutUnit.entries[index]) },
            enabled = controlsEnabled,
        )
        ApplySettingButton(
            field = field,
            state = state,
            enabled = error == null,
            onClick = onApply,
        )
    }
}

@Composable
private fun ApplySettingButton(
    field: SettingsField,
    state: SettingsState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    KNetButton(
        onClick = onClick,
        enabled = enabled && field in state.dirtyFields && !state.isLoading,
        loading = field in state.savingFields,
        variant = ButtonVariant.Secondary,
    ) {
        Text("Apply")
    }
}
