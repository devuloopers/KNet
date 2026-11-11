package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.chip.KNetChip
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedTrafficRuleDraft
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel

/** KNet-native policy workspace for pinned, mutually authenticated, and system-owned traffic. */
@Composable
fun ProtectedTrafficScreen(
    viewModel: ProtectedTrafficViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val ruleListState = rememberLazyListState()
    val spacing = KNetTheme.spacing
    ProtectedTrafficWorkspace(modifier) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = ruleListState, modifier = Modifier.fillMaxSize().padding(end = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                item(key = "default") {
                    DefaultActionCard(state.configuration.defaultAction, viewModel::selectDefaultAction)
                }
                item(key = "rules-heading") {
                    SectionHeader("Manual rules", "Saved rules take priority over the default TLS action.", "Add rule", viewModel::beginAddRule)
                }
                val userRules = state.configuration.rules.filter { it.origin != ProtectedTrafficRuleOrigin.BUILT_IN }
                if (userRules.isEmpty()) item(key = "empty-rules") { EmptyRulesCard(viewModel::beginAddRule) }
                items(userRules, key = { "rule-${it.id.value}" }) { rule ->
                    ProtectedRuleRow(rule, { enabled -> viewModel.setRuleEnabled(rule, enabled) },
                        { viewModel.deleteRule(rule.id) }, { viewModel.beginEditRule(rule) })
                }

            }
            KNetVerticalScrollbar(ruleListState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
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
            title = "Protected Traffic",
            message = error,
            onDismissRequest = viewModel::clearError,
        )
    }
}

@Composable
private fun DefaultActionCard(
    action: ProtectedTrafficAction,
    onActionSelected: (ProtectedTrafficAction) -> Unit,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.medium,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                "Default TLS connection handling",
                style = KNetTheme.typography.caption.copy(
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                ),
            )
            KNetInspectorParagraph(
                "Controls HTTPS, GraphQL over HTTPS, secure WebSocket (WSS) and gRPC over TLS routed through KNet. " +
                    "Plain HTTP, unencrypted WebSocket (ws://), UDP and QUIC/HTTP/3 are outside this setting.",
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                ProtectedTrafficAction.entries.forEach { candidate ->
                    KNetChip(
                        text = candidate.label,
                        selected = action == candidate,
                        onClick = if (candidate == ProtectedTrafficAction.BYPASS) null else ({ onActionSelected(candidate) }),
                        enabled = candidate != ProtectedTrafficAction.BYPASS,
                    )
                }
            }
            KNetInspectorParagraph(
                text = when (action) {
                    ProtectedTrafficAction.INSPECT ->
                        "Attempt TLS decryption to show requests and responses. If inspection fails, select the connection " +
                            "in Traffic to review the reason and save a handling rule."
                    ProtectedTrafficAction.TUNNEL ->
                        "Forward encrypted traffic without decrypting it. Destination, timing and byte counts remain visible."
                    ProtectedTrafficAction.BYPASS ->
                        "This previously saved Bypass setting is handled as an encrypted tunnel by the desktop proxy."
                    ProtectedTrafficAction.BLOCK ->
                        "Reject these connections and record the decision. Traffic outside this setting is unaffected."
                },
            )
            KNetInspectorParagraph("Matching saved rules take priority. Changes apply to new connections.")
            KNetInspectorParagraph(
                "Bypass is disabled: routing directly outside KNet requires a supported packet provider.",
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun ProtectedRuleRow(
    rule: ProtectedTrafficRule,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.small,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    rule.selectorLabel.ifBlank { "All protected traffic" },
                    style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                KNetBadge(
                    text = rule.action.label.uppercase() + if (rule.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET) " · IMPORTED" else "",
                    containerColor = actionContainerColor(rule.action),
                    contentColor = actionContentColor(rule.action),
                )
            }
            KNetSwitch(checked = rule.enabled, onCheckedChange = onEnabledChange)
            KNetIconButton(onClick = onEdit, icon = KNetIcons.Edit, contentDescription = "Edit protected traffic rule")
            KNetIconButton(
                onClick = onDelete,
                icon = KNetIcons.Delete,
                contentDescription = "Delete protected traffic rule",
                tint = colors.semantic.error,
            )
        }
    }
}

@Composable
private fun EmptyRulesCard(onAdd: () -> Unit) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = KNetIcons.Hub,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(KNetTheme.dimensions.iconSizeLarge),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "No custom rules",
                    style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary),
                )
                Text(
                    "Add a rule here or choose future connection handling from a Traffic item.",
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                )
            }
            KNetButton(onClick = onAdd, variant = ButtonVariant.Secondary) { Text("Create first rule") }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = KNetTheme.typography.titleLarge.copy(color = colors.textPrimary))
            Text(subtitle, style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary))
        }
        if (actionText != null && onAction != null) {
            KNetButton(onClick = onAction, variant = ButtonVariant.Primary) {
                Icon(
                    imageVector = KNetIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(KNetTheme.dimensions.iconSizeSmall),
                )
                Spacer(Modifier.width(spacing.xs))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun RuleDialog(
    draft: ProtectedTrafficRuleDraft,
    onChange: (ProtectedTrafficRuleDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    KNetDialog(
        onDismissRequest = onDismiss,
        title = "Add protected traffic rule",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        ) {
            LabeledTextField(
                label = "Application identity",
                value = draft.sourceApplication,
                onValueChange = { onChange(draft.copy(sourceApplication = it)) },
                placeholder = "Package or signing ID (optional)",
            )
            LabeledTextField(
                label = "Destination",
                value = draft.destination,
                onValueChange = { onChange(draft.copy(destination = it)) },
                placeholder = "Host, *.domain, IP, or CIDR",
            )
            LabeledTextField(
                label = "Port",
                value = draft.port,
                onValueChange = { onChange(draft.copy(port = it.filter(Char::isDigit))) },
                placeholder = "Any port",
            )

            SelectionGroup("Transport") {
                ProtectedTransportProtocol.entries.forEach { transport ->
                    KNetChip(
                        text = transport.name,
                        selected = draft.transport == transport,
                        onClick = { onChange(draft.copy(transport = transport)) },
                    )
                }
            }
            SelectionGroup("IP family") {
                listOf(null, ProtectedIpFamily.IPV4, ProtectedIpFamily.IPV6).forEach { family ->
                    KNetChip(
                        text = family?.name ?: "Any IP",
                        selected = draft.ipFamily == family,
                        onClick = { onChange(draft.copy(ipFamily = family)) },
                    )
                }
            }
            SelectionGroup("Action") {
                ProtectedTrafficAction.entries.forEach { action ->
                    KNetChip(
                        text = action.label,
                        selected = draft.action == action,
                        enabled = action != ProtectedTrafficAction.BYPASS,
                        onClick = if (action == ProtectedTrafficAction.BYPASS) null else ({ onChange(draft.copy(action = action)) }),
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KNetButton(onClick = onDismiss, variant = ButtonVariant.Secondary) { Text("Cancel") }
                Spacer(Modifier.width(KNetTheme.spacing.sm))
                KNetButton(onClick = onSave, variant = ButtonVariant.Primary) { Text("Save rule") }
            }
        }
    }
}

@Composable
private fun SelectionGroup(label: String, content: @Composable () -> Unit) {
    val colors = KNetTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
        Text(label, style = KNetTheme.typography.labelMedium.copy(color = colors.textPrimary))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            content()
        }
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = KNetTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Text(label, style = KNetTheme.typography.labelMedium.copy(color = colors.textPrimary))
        KNetTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            config = InputFieldConfig(
                placeholder = placeholder,
                showHoverPopupOnOverflow = false,
            ),
        )
    }
}

@Composable
private fun actionContainerColor(action: ProtectedTrafficAction) = when (action) {
    ProtectedTrafficAction.INSPECT -> KNetTheme.colors.semantic.infoContainer
    ProtectedTrafficAction.TUNNEL -> KNetTheme.colors.semantic.successContainer
    ProtectedTrafficAction.BYPASS -> KNetTheme.colors.semantic.warningContainer
    ProtectedTrafficAction.BLOCK -> KNetTheme.colors.semantic.errorContainer
}

@Composable
private fun actionContentColor(action: ProtectedTrafficAction) = when (action) {
    ProtectedTrafficAction.INSPECT -> KNetTheme.colors.semantic.info
    ProtectedTrafficAction.TUNNEL -> KNetTheme.colors.semantic.success
    ProtectedTrafficAction.BYPASS -> KNetTheme.colors.semantic.warning
    ProtectedTrafficAction.BLOCK -> KNetTheme.colors.semantic.error
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
