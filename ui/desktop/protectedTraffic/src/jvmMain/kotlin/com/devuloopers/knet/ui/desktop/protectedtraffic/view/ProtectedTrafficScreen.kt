package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.KNetSearchField
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.responsive.ResponsiveLayout
import com.devuloopers.knet.ui.core.foundation.responsive.WindowSizeClass
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.displayLabel
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.description
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel

/**
 * Saved-policy manager; connection diagnostics and per-destination quick actions remain in Traffic.
 * @param viewModel Use-case-backed policy state and mutation owner.
 * @param modifier Bounded workspace viewport from the desktop shell.
 */
@Composable
fun ProtectedTrafficScreen(viewModel: ProtectedTrafficViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    ResponsiveLayout(modifier) { windowInfo ->
        val compact = windowInfo.widthSizeClass == WindowSizeClass.Compact
        Box(Modifier.fillMaxSize()) {
            ProtectedTrafficWorkspace(headerActions = {
                Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                    ManagerText("Default TLS handling", caption = true)
                    KNetDropdown(selectedItem = state.configuration.defaultHandling,
                        items = ProtectedTrafficDefaultHandling.entries,
                        itemText = { it.displayLabel }, onItemSelected = viewModel::selectDefaultHandling,
                        itemDescription = { it.description },
                        enabled = state.configuration.configurationReady && !state.isSettingDefault,
                        modifier = Modifier.testTag("Default TLS action"))
                    if (state.configuration.defaultAction == ProtectedTrafficAction.BYPASS)
                        ManagerText("Saved Bypass uses an encrypted tunnel.", caption = true)
                }
            }) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                    if (compact) Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                        RuleSearch(state.searchQuery, viewModel::updateSearchQuery, Modifier.fillMaxWidth())
                        AddRuleButton(state.configuration.configurationReady && !state.isSaving, viewModel::beginAddRule)
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
                        RuleSearch(state.searchQuery, viewModel::updateSearchQuery, Modifier.weight(1f))
                        AddRuleButton(state.configuration.configurationReady && !state.isSaving, viewModel::beginAddRule)
                    }
                    ProtectedRulesTable(state, viewModel::setRuleEnabled, viewModel::beginEditRule, viewModel::deleteRule,
                        onClearSearch = { viewModel.updateSearchQuery("") }, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
            ProtectedRuleDrawer(state, viewModel::updateRuleDraft, viewModel::dismissRuleDraft, viewModel::saveRuleDraft)
        }
    }
    state.errorMessage?.let { error -> AlertDialog(title = "Protected Traffic", message = error, onDismissRequest = viewModel::clearError) }
}

@Composable
private fun RuleSearch(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    KNetSearchField(query, onChange, modifier.testTag("Protected traffic search"), placeholder = "Search destination, application, port or action…")
}

@Composable
private fun AddRuleButton(enabled: Boolean, onAdd: () -> Unit) {
    KNetButton(onClick = onAdd, enabled = enabled, variant = ButtonVariant.Primary) {
        Icon(KNetIcons.Add, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall))
        Spacer(Modifier.width(KNetTheme.spacing.xs))
        Text("Add rule", maxLines = 1, softWrap = false)
    }
}

/** Single-line manager labels inherit KNet typography and overflow behavior. */
@Composable
internal fun ManagerText(text: String, modifier: Modifier = Modifier, caption: Boolean = false) {
    Text(text, modifier, style = if (caption) KNetTheme.typography.caption else KNetTheme.typography.bodySmall,
        color = if (caption) KNetTheme.colors.textSecondary else KNetTheme.colors.textPrimary,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}
