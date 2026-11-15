package com.devuloopers.knet.ui.desktop.httppanel.export

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Stable action slot shared by response headers and Traffic; disabled controls never change their label/width. */
@Composable
fun CaptureExportButton(enabled: Boolean, label: String = "Export", description: String? = null, onClick: () -> Unit) {
    com.devuloopers.knet.ui.core.components.tooltip.KNetTooltipHost(
        description = description ?: if (enabled) "Export this request and received response." else "Send a request and receive a response before exporting.",
    ) {
        KNetButton(onClick = onClick, enabled = enabled, variant = ButtonVariant.Secondary, size = ButtonSize.Compact) {
            Text(label, maxLines = 1, softWrap = false)
        }
    }
}

/** KNet-themed shared preflight, privacy and save UI; serialization stays behind application use cases. */
@Composable
fun CaptureExportDialog(viewModel: CaptureExportViewModel) {
    val state by viewModel.state.collectAsState()
    if (state.source == null) return
    val colors = KNetTheme.colors
    KNetDialog(onDismissRequest = viewModel::dismiss, title = "Export traffic") {
        Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
            Text(state.label, style = KNetTheme.typography.bodyMedium.copy(color = colors.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.scopes.isNotEmpty()) com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown(
                selectedItem = state.scopes.firstOrNull { it.second == state.source } ?: state.scopes.first(),
                items = state.scopes, onItemSelected = { viewModel.open(it.second, it.first, state.scopes) },
                enabled = !state.saving && !state.complete, modifier = Modifier.fillMaxWidth(), itemText = { it.first },
            )
            state.prepared?.let { prepared ->
                Text("${prepared.records} records · ${prepared.messages} messages · ${prepared.missingPayloads} unavailable payloads",
                    style = KNetTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (prepared.unavailableRecords > 0) Text("${prepared.unavailableRecords} selected items are no longer retained and will be omitted.",
                    style = KNetTheme.typography.caption.copy(color = colors.semantic.warning), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown(
                selectedItem = state.options.format,
                items = CaptureExportFormat.entries.filter { it != CaptureExportFormat.HAR || (state.prepared?.httpExchanges ?: 0) > 0 },
                onItemSelected = { viewModel.options(state.options.copy(format = it)) },
                enabled = !state.preparing && !state.saving && !state.complete,
                modifier = Modifier.fillMaxWidth(), itemText = { it.label },
            )
            if (state.options.format == CaptureExportFormat.HAR) state.prepared?.let { prepared ->
                Text("HAR: ${prepared.httpExchanges} HTTP exchanges; ${prepared.records - prepared.httpExchanges} other records and ${prepared.messages} messages omitted.",
                    style = KNetTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Original encoded/binary payloads use base64; request body encoding is a KNet extension.",
                    style = KNetTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Toggle("Include captured payloads", state.options.includePayloads, !state.saving && !state.complete) {
                viewModel.options(state.options.copy(includePayloads = it))
            }
            Toggle("Include authorization and cookies", state.options.includeCredentials, !state.saving && !state.complete) {
                viewModel.options(state.options.copy(includeCredentials = it))
            }
            Text("Payloads and URLs may contain secrets. Header removal does not scrub their contents.",
                style = KNetTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.preparing) Text("Preparing snapshot...", color = colors.textSecondary)
            state.progress?.let { Text("${it.recordsWritten} records written · ${it.bytesWritten} payload bytes", color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            state.error?.let { Text(it, color = colors.semantic.error, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (state.complete) Text("Export saved.", color = colors.semantic.success)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                KNetButton(onClick = viewModel::dismiss, variant = ButtonVariant.Secondary) { Text(if (state.complete) "Done" else "Cancel") }
                Spacer(Modifier.width(KNetTheme.spacing.sm))
                if (!state.complete) KNetButton(onClick = viewModel::save,
                    enabled = state.prepared != null && !state.preparing && !state.saving) {
                    Text(if (state.saving) "Saving..." else "Save export", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, selected: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = KNetTheme.typography.bodySmall.copy(color = KNetTheme.colors.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        KNetSwitch(checked = selected, onCheckedChange = onChange, enabled = enabled)
    }
}
