package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.components.table.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.*

/** Semantic column proportions; minimum readable width and scrolling are shared UI-core policy. */
private enum class RuleColumn(val title: String, val weight: Float) {
    ENABLED("Enabled", .7f), DESTINATION("Destination", 2.7f), APPLICATION("Application", 2.3f),
    PORT("Port", .7f), TRANSPORT("Transport", 1f), ACTION("Action", 1.2f), ACTIONS("Actions", 1f),
}

/** Virtualized rule table uses stable rule IDs and the same finite column layout for headers and rows. */
@Composable
internal fun ProtectedRulesTable(state: ProtectedTrafficState, onEnabled: (ProtectedTrafficRule, Boolean) -> Unit,
    onEdit: (ProtectedTrafficRule) -> Unit, onDelete: (ProtectedTrafficRuleId) -> Unit,
    onClearSearch: () -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val rules = state.filteredRules
    LaunchedEffect(state.searchQuery) { listState.scrollToItem(0) }
    val empty: (@Composable () -> Unit)? = if (rules.isNotEmpty()) null else ({
        Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
            ManagerText(when {
                !state.configuration.configurationReady -> "Loading rules…"
                state.searchQuery.isNotBlank() -> "No matching rules"
                else -> "No saved rules"
            })
            KNetInspectorParagraph(if (state.searchQuery.isNotBlank()) "Try another search or clear it to view all saved rules."
                else "Add a rule here or choose future connection handling from a Traffic item.")
            if (state.searchQuery.isNotBlank()) KNetButton(onClick = onClearSearch, variant = ButtonVariant.Secondary) {
                Text("Clear search", maxLines = 1, softWrap = false)
            }
        }
    })
    KNetTableViewport(listState, modifier.testTag("Protected rules table"), emptyContent = empty) { contentModifier ->
        Column(contentModifier) {
            KNetTableHeader {
                RuleColumn.entries.forEach { column -> KNetCell(column.title, Modifier.weight(column.weight)
                    .padding(horizontal = KNetTheme.spacing.xs), isHeader = true) }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(rules, key = { it.id.value }) { rule ->
                    val enabled = state.configuration.configurationReady && !state.isSaving && rule.id !in state.pendingRuleIds
                    KNetRow(onClick = { onEdit(rule) }, selected = state.ruleDraft?.id == rule.id, enabled = enabled,
                        rowHeight = KNetTheme.dimensions.managerTableRowHeight,
                        modifier = Modifier.testTag("Protected rule ${rule.id.value}")) {
                        RuleColumn.entries.forEach { column ->
                            Box(Modifier.weight(column.weight).padding(horizontal = KNetTheme.spacing.xs), contentAlignment = Alignment.CenterStart) {
                                when (column) {
                                    RuleColumn.ENABLED -> KNetSwitch(rule.enabled, { onEnabled(rule, it) }, enabled = enabled,
                                        modifier = Modifier.testTag("Enable rule ${rule.id.value}"))
                                    RuleColumn.DESTINATION -> Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                                        KNetCell(rule.destination?.displayValue ?: "All destinations", Modifier.weight(1f))
                                        if (rule.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET) KNetBadge("IMPORTED")
                                    }
                                    RuleColumn.APPLICATION -> KNetCell(rule.sourceApplication?.value ?: "Any application")
                                    RuleColumn.PORT -> KNetCell(rule.port?.toString() ?: "All ports")
                                    RuleColumn.TRANSPORT -> KNetCell(listOfNotNull(rule.transport?.name ?: "All", rule.ipFamily?.name).joinToString(" · "))
                                    RuleColumn.ACTION -> ActionBadge(rule.action)
                                    RuleColumn.ACTIONS -> Row(horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                                        KNetIconButton(onClick = { onEdit(rule) }, icon = KNetIcons.Edit,
                                            contentDescription = "Edit rule ${rule.id.value}", enabled = enabled)
                                        KNetIconButton(onClick = { onDelete(rule.id) }, icon = KNetIcons.Delete,
                                            contentDescription = "Delete rule ${rule.id.value}", enabled = enabled, tint = KNetTheme.colors.semantic.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBadge(action: ProtectedTrafficAction) {
    val semantic = KNetTheme.colors.semantic
    val (container, content) = when (action) {
        ProtectedTrafficAction.INSPECT -> semantic.infoContainer to semantic.info
        ProtectedTrafficAction.TUNNEL -> semantic.successContainer to semantic.success
        ProtectedTrafficAction.BLOCK -> semantic.errorContainer to semantic.error
        ProtectedTrafficAction.BYPASS -> semantic.warningContainer to semantic.warning
    }
    KNetBadge(action.displayLabel, containerColor = container, contentColor = content)
}
