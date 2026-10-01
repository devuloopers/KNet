package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedServiceGroupId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficBuiltIns
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedTrafficRuleDraft
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel

/** Dedicated policy workspace for payload-preserving protected traffic and explicit blocking/bypass rules. */
@Composable
fun ProtectedTrafficScreen(
    viewModel: ProtectedTrafficViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val colors = KNetTheme.colors
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Protected Traffic", style = KNetTheme.typography.titleLarge, color = colors.textPrimary)
        Text(
            "Keep pinned, mutually authenticated, and system-owned connections working without decrypting them. " +
                "Tunneled flows remain visible as metadata-only Traffic rows.",
            color = colors.textSecondary,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Default action", style = KNetTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtectedTrafficAction.entries.forEach { action ->
                        FilterChip(
                            selected = state.configuration.defaultAction == action,
                            onClick = { viewModel.selectDefaultAction(action) },
                            label = { Text(action.label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Text(
                    "Inspect is the safe default. Tunnel preserves end-to-end encryption; bypass is available only " +
                        "when the active platform adapter can route outside KNet.",
                    color = colors.textSecondary,
                )
            }
        }

        Text("Compatibility groups", style = KNetTheme.typography.titleMedium, color = colors.textPrimary)
        compatibilityGroups.forEach { group ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(group.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        Text(group.description, color = colors.textSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = group.id in state.configuration.enabledBuiltInGroups,
                        onCheckedChange = { enabled -> viewModel.setCompatibilityGroupEnabled(group.id, enabled) },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Application and destination rules", style = KNetTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Button(onClick = viewModel::beginAddRule) { Text("Add rule", maxLines = 1, softWrap = false) }
        }
        val userRules = state.configuration.rules.filter { it.origin == ProtectedTrafficRuleOrigin.USER }
        if (userRules.isEmpty()) {
            Text("No custom rules.", color = colors.textSecondary, maxLines = 1, softWrap = false)
        }
        userRules.forEach { rule ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.selectorLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        Text(rule.action.label, color = colors.textSecondary, maxLines = 1, softWrap = false)
                    }
                    Switch(rule.enabled, { enabled -> viewModel.setRuleEnabled(rule, enabled) })
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.deleteRule(rule.id) }) {
                        Text("Delete", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }

    state.ruleDraft?.let { draft ->
        RuleDialog(
            draft = draft,
            onChange = viewModel::updateRuleDraft,
            onDismiss = viewModel::dismissRuleDraft,
            onSave = viewModel::saveRuleDraft,
        )
    }
    state.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Protected Traffic", maxLines = 1, softWrap = false) },
            text = { Text(error) },
            confirmButton = { Button(viewModel::clearError) { Text("OK", maxLines = 1, softWrap = false) } },
        )
    }
}

@Composable
private fun RuleDialog(
    draft: ProtectedTrafficRuleDraft,
    onChange: (ProtectedTrafficRuleDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add protected traffic rule", maxLines = 1, softWrap = false) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.sourceApplication,
                    onValueChange = { onChange(draft.copy(sourceApplication = it)) },
                    label = { Text("Application package/signing ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.destination,
                    onValueChange = { onChange(draft.copy(destination = it)) },
                    label = { Text("Host, *.domain, IP, or CIDR") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.port,
                    onValueChange = { onChange(draft.copy(port = it)) },
                    label = { Text("Port (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtectedTransportProtocol.entries.forEach { transport ->
                        FilterChip(
                            selected = draft.transport == transport,
                            onClick = { onChange(draft.copy(transport = transport)) },
                            label = { Text(transport.name, maxLines = 1, softWrap = false) },
                        )
                    }
                    listOf(null, ProtectedIpFamily.IPV4, ProtectedIpFamily.IPV6).forEach { family ->
                        FilterChip(
                            selected = draft.ipFamily == family,
                            onClick = { onChange(draft.copy(ipFamily = family)) },
                            label = { Text(family?.name ?: "Any IP", maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtectedTrafficAction.entries.forEach { action ->
                        FilterChip(
                            selected = draft.action == action,
                            onClick = { onChange(draft.copy(action = action)) },
                            label = { Text(action.label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save", maxLines = 1, softWrap = false) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel", maxLines = 1, softWrap = false) } },
    )
}

private val ProtectedTrafficAction.label: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private val ProtectedTrafficRule.selectorLabel: String
    get() = listOfNotNull(
        sourceApplication?.value?.let { "app $it" },
        destination?.displayValue,
        port?.let { "port $it" },
        transport?.name,
        ipFamily?.name,
    ).joinToString(" · ")

private val ProtectedDestinationSelector.displayValue: String
    get() = when (this) {
        is ProtectedDestinationSelector.Exact -> value
        is ProtectedDestinationSelector.WildcardDomain -> "*.$suffix"
        is ProtectedDestinationSelector.Cidr -> "$networkAddress/$prefixLength"
    }

private data class CompatibilityGroup(
    val id: ProtectedServiceGroupId,
    val title: String,
    val description: String,
)

private val compatibilityGroups = listOf(
    CompatibilityGroup(
        ProtectedTrafficBuiltIns.GooglePlayBillingGroupId,
        "Google Play Billing",
        "Tunnel verified Play Store traffic so product and subscription loading can remain functional.",
    ),
    CompatibilityGroup(
        ProtectedTrafficBuiltIns.GoogleSystemServicesGroupId,
        "Google system services",
        "Optional broader tunnel for verified Play services and Google Services Framework traffic.",
    ),
    CompatibilityGroup(
        ProtectedTrafficBuiltIns.AppleAppStoreGroupId,
        "Apple App Store and StoreKit",
        "Tunnel verified App Store or StoreKit agent flows when the active Apple provider exposes them.",
    ),
)
