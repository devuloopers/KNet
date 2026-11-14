package com.devuloopers.knet.ui.desktop.breakpointmanager.view

import com.devuloopers.knet.ui.core.components.policy.*
import com.devuloopers.knet.domain.policy.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.layout.KNetScreenLayout
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.AddEditBreakpointRuleDrawer
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.BreakpointRulesTable
import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel

/**
 * Main Breakpoint Manager composition screen matching the approved design specification.
 */
@Composable
fun BreakpointManagerScreen(
    viewModel: BreakpointManagerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        KNetScreenLayout(
            title = "Intercepts",
            subtitle = "${state.workspaceName} · Rules pause matching traffic only for this device.",
            actions = {
                Row(
                    modifier = Modifier
                        .background(themeColors.surfaceVariant, RoundedCornerShape(6.dp))
                        .border(1.dp, themeColors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val statusText = if (state.isGlobalInterceptionEnabled) "ENABLED" else "DISABLED"
                    Text(
                        text = "Interception: $statusText",
                        style = typography.caption.copy(
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    KNetSwitch(
                        checked = state.isGlobalInterceptionEnabled,
                        onCheckedChange = { viewModel.toggleGlobalInterception(it) }
                    )
                }
            },
        ) {
            // Controls Bar (Search Bar & + Add Rule Primary Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .widthIn(min = 160.dp, max = 320.dp)
                        .weight(1f, fill = false),
                    config = InputFieldConfig(
                        placeholder = "Search rules...",
                        backgroundColor = themeColors.surfaceVariant,
                        borderColor = themeColors.border
                    )
                )

                KNetButton(
                    onClick = { viewModel.openAddDrawer() },
                    variant = ButtonVariant.Primary
                ) {
                    Text(
                        text = "+ Add Rule",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Breakpoint Rules Data Table
            BreakpointRulesTable(
                rules = state.filteredRules,
                libraryLabel = { id -> if (state.definitions.firstOrNull { it.rule.id == id }?.availability == RuleAvailability.Shared) RuleLibraryLocation.SHARED.label else RuleLibraryLocation.PRIVATE.label },
                onToggleStatus = { viewModel.toggleRuleStatus(it) },
                onEditRule = { viewModel.openEditDrawer(it) },
                onDeleteRule = { viewModel.requestRuleDeletion(it) },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        AddEditBreakpointRuleDrawer(
            visible = state.isAddEditDrawerVisible,
            rule = state.editingRule,
            isEditingExistingRule = state.editingContext.expectedRevision != null,
            libraryContent = {
                RuleLibraryEditor(RuleLibraryEditorState(state.editingWorkspaceName,
                    if (state.editingContext.availability == RuleAvailability.Shared) RuleLibraryLocation.SHARED else RuleLibraryLocation.PRIVATE,
                    state.editingContext.expectedRevision != null, state.editingUsageNames, sharedDefinition = state.editingContext.originalAvailability == com.devuloopers.knet.domain.policy.RuleAvailability.Shared),
                    viewModel::setDraftShared, viewModel::duplicateDraft)
            },
            saving = state.isSaving,
            protocolDefinitions = state.protocolDefinitions,
            initialProtocolValues = state.editingProtocolValues,
            onDismiss = viewModel::closeDrawer,
            onSave = { urlPattern, portCriteria, method, phase, enabled, protocolId, protocolValues ->
                viewModel.saveRule(
                    urlPattern,
                    method,
                    phase,
                    enabled,
                    portCriteria,
                    protocolId,
                    protocolValues,
                )
            },
        )
    }
    state.pendingDeletion?.let { request ->
        com.devuloopers.knet.ui.core.components.policy.RuleLibraryDeleteDialog(
            request.affectedWorkspaceNames, viewModel::confirmRuleDeletion, viewModel::dismissRuleDeletion)
    }
    state.errorMessage?.let { error ->
        com.devuloopers.knet.ui.core.components.dialog.AlertDialog("Intercepts", error, viewModel::clearError)
    }
}
