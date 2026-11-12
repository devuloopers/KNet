package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
import com.devuloopers.knet.ui.core.components.layout.KNetScreenLayout
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.components.table.KNetTableViewport
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltip
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.responsive.ResponsiveLayout
import com.devuloopers.knet.ui.core.foundation.responsive.WindowSizeClass
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsState
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsTab
import com.devuloopers.knet.ui.desktop.networkconditions.model.hasObservedTraffic
import com.devuloopers.knet.ui.desktop.networkconditions.model.throughputReference
import com.devuloopers.knet.ui.desktop.networkconditions.model.noThrottlingProfileName
import com.devuloopers.knet.ui.desktop.networkconditions.model.globalProfileOptions
import com.devuloopers.knet.ui.desktop.networkconditions.model.selectedGlobalProfile

/** Theme-owned panels and responsive size classes keep policy, measured traffic and saved rules distinct. */
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
    val tabState = rememberSaveableStateHolder()
    ResponsiveLayout(modifier.background(KNetTheme.colors.background)) { windowInfo ->
        val compact = windowInfo.heightSizeClass == WindowSizeClass.Compact
        val compactWidth = windowInfo.widthSizeClass == WindowSizeClass.Compact
        KNetScreenLayout(
            title = "Network Conditions",
            subtitle = if (state.configuration.enabled) "Global conditions apply unless a rule overrides them."
                else "Conditions bypassed. Saved profiles and rules are retained.",
            actions = {
                KNetSwitch(state.configuration.enabled, onEnabledChange,
                    label = if (state.configuration.enabled) "Enabled" else "Disabled")
                KNetButton(onReset, variant = ButtonVariant.Secondary, size = ButtonSize.Compact) {
                    Text("Reset", maxLines = 1, softWrap = false)
                }
            },
        ) {
            GlobalConditionToolbar(state, onSelectProfile, compactWidth, compact)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    NetworkConditionsTab.entries.forEach { tab ->
                        val selected = state.activeTab == tab
                        val accent = KNetTheme.colors.accent
                        val underline = KNetTheme.dimensions.borderWidth * 2
                        KNetTab(tab.title, selected, { onSelectTab(tab) }, modifier = Modifier.drawWithContent {
                            drawContent()
                            if (selected) drawRect(accent, Offset(0f, size.height - underline.toPx()),
                                Size(size.width, underline.toPx()))
                        })
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.activeTab != NetworkConditionsTab.OVERVIEW) KNetButton(
                    onClick = if (state.activeTab == NetworkConditionsTab.RULES) onAddRule else onNewProfile,
                    size = ButtonSize.Compact,
                ) {
                    Icon(KNetIcons.Add, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall))
                    Spacer(Modifier.width(spacing.xs))
                    Text(if (state.activeTab == NetworkConditionsTab.RULES) "Add rule" else "New custom profile",
                        maxLines = 1, softWrap = false)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Tab-local list scroll state survives navigation; telemetry remains owned by the screen ViewModel.
                tabState.SaveableStateProvider(state.activeTab) {
                    when (state.activeTab) {
                        NetworkConditionsTab.OVERVIEW -> ThroughputMonitor(state, compact, Modifier.fillMaxSize())
                        NetworkConditionsTab.RULES -> RulesTable(state, onRuleEnabledChange, onEditRule, onDeleteRule)
                        NetworkConditionsTab.PROFILES -> ProfilesTable(state.configuration.profiles, onEditProfile, onDeleteProfile)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalConditionToolbar(state: NetworkConditionsState, onSelectProfile: (NetworkConditionProfileId?) -> Unit,
    compactWidth: Boolean, compact: Boolean) {
    val selected = state.configuration.selectedGlobalProfile
    val options = remember(state.configuration.profiles) { state.configuration.globalProfileOptions }
    val summary = selected?.let { "MTU ${it.virtualMtuBytes} · ${it.failure.summary()}" } ?: "No global limit"
    WorkspacePanel {
        Row(Modifier.fillMaxWidth().padding(if (compact) KNetTheme.spacing.md else KNetTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
            Column(Modifier.weight(1.8f), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                SmallLabel(if (compactWidth && compact) "Global profile · $summary" else "Global profile")
                KNetDropdown(
                    selectedItem = selected, items = options, onItemSelected = { onSelectProfile(it?.id) },
                    defaultItem = null, itemText = { it?.name ?: noThrottlingProfileName },
                    size = KNetDropdownSize.Standard, modifier = Modifier.fillMaxWidth(),
                )
                if (compactWidth && !compact) SmallLabel(summary)
            }
            MetricDivider()
            PolicyMetric("Download", selected?.let { rate(it.download.effectiveBitsPerSecond) } ?: "Unlimited", Modifier.weight(1f))
            MetricDivider()
            PolicyMetric("Upload", selected?.let { rate(it.upload.effectiveBitsPerSecond) } ?: "Unlimited", Modifier.weight(1f))
            MetricDivider()
            PolicyMetric("Latency", "${selected?.latencyMillis ?: 0} ms", Modifier.weight(0.8f))
            MetricDivider()
            PolicyMetric("Jitter", "${selected?.jitterMillis ?: 0} ms", Modifier.weight(0.7f))
            if (!compactWidth) {
                MetricDivider()
                Box(Modifier.weight(1.3f)) { SmallLabel(summary) }
            }
        }
    }
}

@Composable
private fun PolicyMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        SmallLabel(label)
        Text(value, style = KNetTheme.typography.titleMedium.copy(color = KNetTheme.colors.textPrimary),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** A short visual separator uses the same density tokens as the existing KNet controls. */
@Composable
private fun MetricDivider(compact: Boolean = false) {
    Box(Modifier.width(KNetTheme.dimensions.borderWidth)
        .height(if (compact) KNetTheme.dimensions.iconSizeMedium else KNetTheme.dimensions.inputHeightStandard + KNetTheme.spacing.md)
        .background(KNetTheme.colors.border))
}

/** Screen-local composition of the existing KNet surface; no palette or shared component behavior is overridden. */
@Composable
private fun WorkspacePanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    KNetSurface(modifier.fillMaxWidth(), color = KNetTheme.colors.surface, shape = KNetTheme.shapes.medium,
        border = BorderStroke(KNetTheme.dimensions.borderWidth, KNetTheme.colors.border),
        contentAlignment = Alignment.TopStart, content = content)
}

@Composable
private fun ThroughputMonitor(state: NetworkConditionsState, compact: Boolean, modifier: Modifier = Modifier) {
    val reference = throughputReference(state)
    val history = state.throughput
    val observed = history.currentSampleIsValid && history.hasObservedTraffic()
    WorkspacePanel(modifier) {
        Column(Modifier.fillMaxSize().padding(if (compact) KNetTheme.spacing.md else KNetTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(if (compact) KNetTheme.spacing.sm else KNetTheme.spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xl)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                    Text("Actual throughput", style = KNetTheme.typography.titleMedium.copy(color = KNetTheme.colors.textPrimary),
                        maxLines = 1, softWrap = false)
                    if (!compact) SmallLabel("Measured KNet payload")
                }
                Legend("Download ${if (observed) rate(history.currentDownloadBitsPerSecond) else "—"}", KNetTheme.colors.accent)
                Legend("Upload ${if (observed) rate(history.currentUploadBitsPerSecond) else "—"}", KNetTheme.colors.semantic.success)
            }
            LiveThroughputGraph(history, reference.downloadBitsPerSecond, reference.uploadBitsPerSecond,
                state.runtime.activeFlows, state.runtime.queuedBytes, measurementContext = reference.label,
                modifier = Modifier.weight(1f).fillMaxWidth())
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
                RuntimeMetric("Active flows", state.runtime.activeFlows.toString(), compact, Modifier.weight(1f))
                MetricDivider(compact)
                RuntimeMetric("Queued", formatBytes(state.runtime.queuedBytes), compact, Modifier.weight(1f))
                MetricDivider(compact)
                RuntimeMetric("Downloaded", formatBytes(state.runtime.downloadedBytes), compact, Modifier.weight(1f))
                MetricDivider(compact)
                RuntimeMetric("Uploaded", formatBytes(state.runtime.uploadedBytes), compact, Modifier.weight(1f))
            }
        }
    }
}

/** Compact-height workspaces retain the same counters on one line to leave room for usable table rows. */
@Composable
private fun RuntimeMetric(label: String, value: String, compact: Boolean, modifier: Modifier) {
    if (compact) Text("$label $value", modifier, style = KNetTheme.typography.bodySmall,
        color = KNetTheme.colors.textSecondary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    else PolicyMetric(label, value, modifier)
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Box(Modifier.size(7.dp).background(color, KNetTheme.shapes.small))
        Text(text, style = KNetTheme.typography.bodySmall.copy(color = KNetTheme.colors.textPrimary),
            maxLines = 1, softWrap = false)
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
        WorkspacePanel(Modifier.heightIn(max = KNetTheme.spacing.massive * 4).fillMaxHeight()) {
            Box(Modifier.fillMaxSize().padding(KNetTheme.spacing.lg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                    Icon(KNetIcons.Hub, null, Modifier.size(KNetTheme.spacing.xxxl), tint = KNetTheme.colors.textMuted)
                    Text("No condition rules", style = KNetTheme.typography.titleMedium.copy(color = KNetTheme.colors.textPrimary),
                        maxLines = 1, softWrap = false)
                    SmallLabel("Add a rule here or from a Traffic row.")
                }
            }
        }
        return
    }
    ConditionTable(header = {
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
    ConditionTable(header = {
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
private fun ConditionTable(header: @Composable RowScope.() -> Unit, content: LazyListScope.() -> Unit) {
    val vertical = rememberLazyListState()
    KNetTableViewport(vertical, Modifier.fillMaxSize(), header = { headerModifier ->
        Row(headerModifier.background(KNetTheme.colors.panelHeader)
            .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm), content = header)
    }) { contentModifier ->
        LazyColumn(contentModifier, state = vertical, content = content)
    }
}

@Composable
private fun TableRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier, style = KNetTheme.typography.labelMedium.copy(color = KNetTheme.colors.textSecondary),
        maxLines = 1, softWrap = false)
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
        Text(text, style = style.copy(color = color), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text, style = KNetTheme.typography.caption.copy(color = KNetTheme.colors.textSecondary),
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}
