package com.devuloopers.knet.ui.core.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltipHost
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Shared tab status rendering; feature code maps domain state rather than defining colors/overflow. */
enum class WorkspaceTabStatus { NEUTRAL, CONNECTED, RECONNECTING, OFFLINE }

/**
 * Optional close affordance with a stable interaction slot in every closable workspace tab.
 * @property enabled Whether the current workspace can be closed.
 * @property description Accessible label and hover explanation, including why Close is unavailable.
 */
data class WorkspaceTabCloseState(val enabled: Boolean, val description: String)

/**
 * Stable, body-free workspace-tab presentation with no networking responsibility.
 * @property id Non-secret stable selection key.
 * @property label One-line display text, truncated by shared theme geometry.
 * @property status Current connection indication; it never determines whether historical tabs can be selected.
 * @property close Optional independently actionable close control; null omits the control entirely.
 * @property secondaryLabel Optional identity suffix shown with quieter typography on the same line.
 */
data class WorkspaceTabItem(val id: String, val label: String, val status: WorkspaceTabStatus = WorkspaceTabStatus.NEUTRAL,
    val close: WorkspaceTabCloseState? = null, val secondaryLabel: String? = null)

/**
 * Theme-owned compact rounded tabs with a blue selected outline/tint and single-line labels.
 * Controls remain centered inside the existing native strip height with space between each tab.
 * The caller owns selection and any native title-bar inset; offline tabs remain interactive.
 */
@Composable
fun KNetWorkspaceTabs(items: List<WorkspaceTabItem>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, onInteractiveBoundsChanged: (List<Rect>) -> Unit = {},
    onClose: (String) -> Unit = {}) {
    val bounds = remember { mutableMapOf<String, Rect>() }
    DisposableEffect(items.map { it.id }) {
        onDispose { bounds.clear(); onInteractiveBoundsChanged(emptyList()) }
    }
    val horizontal = rememberScrollState()
    val colors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    val spacing = KNetTheme.spacing
    val typography = KNetTheme.typography
    Column(modifier.background(colors.background)) {
        Row(Modifier.fillMaxWidth().height(dimensions.workspaceTabHeight).drawBehind {
            val thickness = dimensions.dividerThickness.toPx()
            drawLine(colors.border, Offset(0f, size.height - thickness / 2),
                Offset(size.width, size.height - thickness / 2), thickness)
        }.horizontalScroll(horizontal).padding(horizontal = spacing.xs).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.workspaceTabGap)) {
            items.forEach { item -> key(item.id) {
                val selected = item.id == selectedId
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val focused by interaction.collectIsFocusedAsState()
                val shape = RoundedCornerShape(dimensions.workspaceTabCornerRadius)
                val fill = when {
                    selected -> colors.interaction.selectedOverlay.compositeOver(colors.surfaceVariant)
                    hovered || focused -> colors.interaction.hoverOverlay.compositeOver(colors.surfaceVariant)
                    else -> colors.surfaceVariant
                }
                Row(Modifier.widthIn(min = dimensions.workspaceTabMinWidth, max = dimensions.workspaceTabMaxWidth)
                    .height(dimensions.workspaceTabControlHeight)
                    .onGloballyPositioned { coordinates ->
                        bounds[item.id] = coordinates.boundsInWindow()
                        onInteractiveBoundsChanged(bounds.values.toList())
                    }.clip(shape)
                    .background(fill)
                    .then(if (selected || focused) Modifier.border(dimensions.borderWidth,
                        if (focused) colors.interaction.focusRing else colors.accent, shape) else Modifier)
                    .selectable(selected = selected, interactionSource = interaction, indication = null,
                        role = Role.Tab, onClick = { onSelect(item.id) })
                    .semantics { contentDescription = listOfNotNull(item.label, item.secondaryLabel,
                        item.status.name.lowercase()).joinToString(", ") }
                    .padding(horizontal = spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (item.status != WorkspaceTabStatus.NEUTRAL) {
                        val color = when (item.status) {
                            WorkspaceTabStatus.CONNECTED -> KNetTheme.colors.semantic.success
                            WorkspaceTabStatus.RECONNECTING -> KNetTheme.colors.accent
                            else -> KNetTheme.colors.textSecondary
                        }
                        Box(Modifier.size(KNetTheme.spacing.sm).clip(CircleShape).background(color))
                    }
                    val fullLabel = listOfNotNull(item.label, item.secondaryLabel).joinToString(" · ")
                    // Limit full-name help to the name region so it cannot overlap Close's own help.
                    KNetTooltipHost(fullLabel, modifier = Modifier.weight(1f, fill = false)) { _ ->
                        Text(item.label, color = colors.textPrimary, style = typography.titleSmall,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    item.secondaryLabel?.let { suffix ->
                        Text("· $suffix", color = colors.textSecondary, style = typography.bodySmall,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.status == WorkspaceTabStatus.OFFLINE) Text("Offline", style = KNetTheme.typography.caption,
                        color = KNetTheme.colors.textSecondary, maxLines = 1, softWrap = false)
                    item.close?.let { close ->
                        KNetTooltipHost(close.description) { _ ->
                            KNetIconButton(
                                onClick = { onClose(item.id) }, icon = KNetIcons.Close,
                                contentDescription = close.description, enabled = close.enabled,
                                size = KNetTheme.dimensions.buttonHeightCompact,
                                iconSize = KNetTheme.dimensions.iconSizeSmall,
                            )
                        }
                    }
                }
            } }
        }
        KNetHorizontalScrollbar(horizontal)
    }
}
