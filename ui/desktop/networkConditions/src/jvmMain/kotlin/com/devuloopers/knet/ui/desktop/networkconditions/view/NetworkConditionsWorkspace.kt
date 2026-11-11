package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownSize
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltip
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsState
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsTab
import com.devuloopers.knet.ui.desktop.networkconditions.model.hasObservedTraffic
import com.devuloopers.knet.ui.desktop.networkconditions.model.throughputReference

/** One constrained workspace: policy controls stay visible while only table rows scroll. */
@Composable
internal fun NetworkConditionsWorkspace(
    state: NetworkConditionsState,
    onEnabledChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSelectProfile: (NetworkConditionProfileId?) -> Unit,
    onSelectTab: (NetworkConditionsTab) -> Unit,
    onAddRule: () -> Unit,
    onRuleEnabledChange: (NetworkConditionRule, Boolean) -> Unit,
    onEditRule: (NetworkConditionRule) -> Unit,
    onDeleteRule: (NetworkConditionRuleId) -> Unit,
    onNewProfile: () -> Unit,
    onEditProfile: (NetworkConditionProfile) -> Unit,
    onDeleteProfile: (NetworkConditionProfileId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = KNetTheme.spacing
    BoxWithConstraints(modifier.fillMaxSize().background(KNetTheme.colors.background)) {
        val compact = maxHeight < 680.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = spacing.xxl, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(if (compact) spacing.sm else spacing.md),
        ) {
            WorkspaceHeader(state.configuration.enabled, onEnabledChange, onReset)
            HorizontalDivider()
            GlobalConditionToolbar(state, onSelectProfile)
            HorizontalDivider()
            ThroughputMonitor(state, compact)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KNetTabRow {
                    NetworkConditionsTab.entries.forEach { tab ->
                        KNetTab(tab.title, state.activeTab == tab, { onSelectTab(tab) })
                    }
                }
                Spacer(Modifier.weight(1f))
                KNetButton(
                    onClick = if (state.activeTab == NetworkConditionsTab.RULES) onAddRule else onNewProfile,
                    size = ButtonSize.Compact,
                ) {
                    Icon(KNetIcons.Add, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall))
                    Spacer(Modifier.width(spacing.xs))
                    Text(if (state.activeTab == NetworkConditionsTab.RULES) "Add rule" else "New custom profile")
                }
            }
            key(state.activeTab) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.activeTab) {
                        NetworkConditionsTab.RULES -> RulesTable(state, onRuleEnabledChange, onEditRule, onDeleteRule)
                        NetworkConditionsTab.PROFILES -> ProfilesTable(state.configuration.profiles, onEditProfile, onDeleteProfile)
                    }
                }
            }
            HorizontalDivider()
            Text(
                "Packet conditions: Android UDP experimental · iOS and desktop packet shaping unavailable",
                style = KNetTheme.typography.caption.copy(color = KNetTheme.colors.textSecondary),
            )
        }
    }
}

@Composable
private fun WorkspaceHeader(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
            Text("Network Conditions", style = KNetTheme.typography.display.copy(color = KNetTheme.colors.textPrimary))
            Text(
                if (enabled) "Global conditions apply unless a rule overrides them."
                else "Conditions bypassed. Saved profiles and rules are retained.",
                style = KNetTheme.typography.bodySmall.copy(color = KNetTheme.colors.textSecondary),
            )
        }
        KNetSwitch(enabled, onEnabledChange, label = if (enabled) "Enabled" else "Disabled")
        KNetButton(onReset, variant = ButtonVariant.Secondary, size = ButtonSize.Compact) { Text("Reset") }
    }
}

@Composable
private fun GlobalConditionToolbar(state: NetworkConditionsState, onSelectProfile: (NetworkConditionProfileId?) -> Unit) {
    val selected = state.configuration.profile(state.configuration.globalProfileId)
    val options = remember(state.configuration.profiles) { listOf<NetworkConditionProfile?>(null) + state.configuration.profiles }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 1050.dp
        val selector: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                SmallLabel("Global profile")
                KNetDropdown(
                    selectedItem = selected, items = options, onItemSelected = { onSelectProfile(it?.id) },
                    defaultItem = null, itemText = { it?.name ?: "Domain rules only" },
                    size = KNetDropdownSize.Compact,
                )
            }
        }
        val metrics: @Composable () -> Unit = {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                PolicyMetric("Download", selected?.let { rate(it.download.effectiveBitsPerSecond) } ?: "Unlimited")
                PolicyMetric("Upload", selected?.let { rate(it.upload.effectiveBitsPerSecond) } ?: "Unlimited")
                PolicyMetric("Latency", "${selected?.latencyMillis ?: 0} ms")
                PolicyMetric("Jitter", "${selected?.jitterMillis ?: 0} ms")
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xxl)) {
                selector()
                Box(Modifier.weight(1f)) { metrics() }
                Text(selected?.let { "MTU ${it.virtualMtuBytes} · ${it.failure.summary()}" } ?: "No global limit",
                    style = KNetTheme.typography.bodySmall.copy(color = KNetTheme.colors.textSecondary))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
                Box(Modifier.weight(1f)) { selector() }
                Box(Modifier.weight(2f)) { metrics() }
            }
        }
    }
}

@Composable
private fun PolicyMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        SmallLabel(label)
        Text(value, style = KNetTheme.typography.titleMedium.copy(color = KNetTheme.colors.textPrimary))
    }
}

@Composable
private fun ThroughputMonitor(state: NetworkConditionsState, compact: Boolean) {
    val reference = throughputReference(state)
    val history = state.throughput
    val observed = history.currentSampleIsValid && history.hasObservedTraffic()
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
            Text("Actual throughput", style = KNetTheme.typography.titleMedium.copy(color = KNetTheme.colors.textPrimary))
            SmallLabel("KNet payload · ${reference.label}")
            Legend("Download ${if (observed) rate(history.currentDownloadBitsPerSecond) else "—"}", KNetTheme.colors.accent)
            Legend("Upload ${if (observed) rate(history.currentUploadBitsPerSecond) else "—"}", KNetTheme.colors.semantic.success)
        }
        LiveThroughputGraph(history, reference.downloadBitsPerSecond, reference.uploadBitsPerSecond,
            state.runtime.activeFlows, state.runtime.queuedBytes, compact = compact)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xl)) {
            SmallLabel("${state.runtime.activeFlows} active flows")
            SmallLabel("${formatBytes(state.runtime.queuedBytes)} queued")
            SmallLabel("Downloaded ${formatBytes(state.runtime.downloadedBytes)}")
            SmallLabel("Uploaded ${formatBytes(state.runtime.uploadedBytes)}")
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Box(Modifier.size(7.dp).background(color, KNetTheme.shapes.small))
        Text(text, style = KNetTheme.typography.bodySmall.copy(color = KNetTheme.colors.textPrimary))
    }
}

@Composable
private fun RulesTable(
    state: NetworkConditionsState,
    onEnabledChange: (NetworkConditionRule, Boolean) -> Unit,
    onEdit: (NetworkConditionRule) -> Unit,
    onDelete: (NetworkConditionRuleId) -> Unit,
) {
    if (state.configuration.rules.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                Text("No condition rules", style = KNetTheme.typography.titleMedium.copy(color = KNetTheme.colors.textPrimary))
                SmallLabel("Add a rule here or use Add to Network Conditions from Traffic.")
            }
        }
        return
    }
    ConditionTable(minimumWidth = 1000.dp, header = {
        HeaderCell("Enabled", Modifier.width(72.dp))
        HeaderCell("Destination", Modifier.weight(1f))
        HeaderCell("Match criteria", Modifier.weight(1.5f))
        HeaderCell("Profile", Modifier.weight(0.9f))
        HeaderCell("Priority", Modifier.width(64.dp))
        Spacer(Modifier.width(72.dp))
    }) {
        items(state.configuration.rules, key = { it.id.value }) { rule ->
            val details = state.ruleDetails[rule.id]
            val textColor = if (rule.enabled) KNetTheme.colors.textPrimary else KNetTheme.colors.textMuted
            TableRow {
                Box(Modifier.width(72.dp)) {
                    KNetSwitch(rule.enabled, { onEnabledChange(rule, it) },
                        modifier = Modifier.semantics { contentDescription = "Enable rule for ${rule.target.displayValue}" })
                }
                TableText(rule.target.displayValue, Modifier.weight(1f), color = textColor, code = true)
                Column(Modifier.weight(1.5f).padding(end = KNetTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                    TableText(details?.protocol ?: "Unavailable protocol", color = textColor)
                    TableText(details?.criteria ?: "Open the rule to review its criteria", color = KNetTheme.colors.textSecondary,
                        small = true)
                }
                TableText(state.configuration.profile(rule.profileId)?.name ?: "Missing profile", Modifier.weight(0.9f), color = textColor)
                TableText(rule.priority.toString(), Modifier.width(64.dp), color = textColor)
                Row(Modifier.width(72.dp)) {
                    KNetIconButton({ onEdit(rule) }, KNetIcons.Edit, "Edit rule for ${rule.target.displayValue}")
                    KNetIconButton({ onDelete(rule.id) }, KNetIcons.Delete, "Delete rule for ${rule.target.displayValue}")
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ProfilesTable(profiles: List<NetworkConditionProfile>, onEdit: (NetworkConditionProfile) -> Unit,
    onDelete: (NetworkConditionProfileId) -> Unit) {
    ConditionTable(minimumWidth = 1000.dp, header = {
        HeaderCell("Name", Modifier.weight(1.2f))
        HeaderCell("Download", Modifier.width(100.dp))
        HeaderCell("Upload", Modifier.width(100.dp))
        HeaderCell("Latency", Modifier.width(90.dp))
        HeaderCell("Details", Modifier.weight(1.7f))
        Spacer(Modifier.width(72.dp))
    }) {
        items(profiles, key = { it.id.value }) { profile ->
            TableRow {
                Column(Modifier.weight(1.2f).padding(end = KNetTheme.spacing.md)) {
                    TableText(profile.name)
                    SmallLabel(if (profile.builtIn) "Built-in" else "Custom")
                }
                TableText(rate(profile.download.effectiveBitsPerSecond), Modifier.width(100.dp))
                TableText(rate(profile.upload.effectiveBitsPerSecond), Modifier.width(100.dp))
                TableText("${profile.latencyMillis} ms", Modifier.width(90.dp))
                TableText(profileSummary(profile), Modifier.weight(1.7f), small = true)
                Row(Modifier.width(72.dp)) {
                    if (!profile.builtIn) {
                        KNetIconButton({ onEdit(profile) }, KNetIcons.Edit, "Edit ${profile.name}")
                        KNetIconButton({ onDelete(profile.id) }, KNetIcons.Delete, "Delete ${profile.name}")
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** A shared header/body scroll owner keeps columns aligned and lazily composes large lists. */
@Composable
private fun ConditionTable(minimumWidth: Dp, header: @Composable RowScope.() -> Unit, content: LazyListScope.() -> Unit) {
    val horizontal = rememberScrollState()
    val vertical = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tableWidth = maxOf(maxWidth - KNetTheme.dimensions.scrollbarWidth, minimumWidth)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxSize().padding(end = KNetTheme.dimensions.scrollbarWidth).horizontalScroll(horizontal)) {
                    Column(Modifier.width(tableWidth).fillMaxHeight()) {
                        Row(Modifier.fillMaxWidth().background(KNetTheme.colors.panelHeader)
                            .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm), content = header)
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = vertical, content = content)
                    }
                }
                KNetVerticalScrollbar(vertical, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            KNetHorizontalScrollbar(horizontal, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TableRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier, style = KNetTheme.typography.labelMedium.copy(color = KNetTheme.colors.textSecondary))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableText(text: String, modifier: Modifier = Modifier, color: Color = KNetTheme.colors.textPrimary,
    code: Boolean = false, small: Boolean = false) {
    TooltipArea(tooltip = { KNetTooltip(text, Modifier.widthIn(max = KNetTheme.dimensions.inspectorMaxWidth)) },
        modifier = modifier.padding(end = KNetTheme.spacing.sm)) {
        val style: TextStyle = when {
            code -> KNetTheme.typography.codeMedium
            small -> KNetTheme.typography.caption
            else -> KNetTheme.typography.bodySmall
        }
        Text(text, style = style.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text, style = KNetTheme.typography.caption.copy(color = KNetTheme.colors.textSecondary))
}
