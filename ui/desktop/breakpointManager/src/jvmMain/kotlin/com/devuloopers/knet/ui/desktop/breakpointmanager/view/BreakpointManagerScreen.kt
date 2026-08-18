package com.devuloopers.knet.ui.desktop.breakpointmanager.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.ui.desktop.breakpointmanager.components.AddEditBreakpointRuleDialog
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
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Bar & Global Switch Container
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title & Description
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Breakpoint Manager",
                        style = typography.titleLarge.copy(
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Define rules to pause in-flight HTTP requests and responses",
                        style = typography.caption.copy(
                            color = themeColors.textMuted,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Global Interception Switch Box
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
                        text = "Global Interception: $statusText",
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
            }

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
                    onClick = { viewModel.openAddDialog() },
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
                onToggleStatus = { viewModel.toggleRuleStatus(it) },
                onEditRule = { viewModel.openEditDialog(it) },
                onDeleteRule = { viewModel.deleteRule(it) },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        // Add / Edit Modal Dialog
        if (state.isAddEditDialogVisible) {
            AddEditBreakpointRuleDialog(
                rule = state.editingRule,
                onDismiss = { viewModel.closeDialog() },
                onSave = { urlPattern, method, phase, enabled, protocolCriteria ->
                    viewModel.saveRule(urlPattern, method, phase, enabled, protocolCriteria)
                }
            )
        }
    }
}
