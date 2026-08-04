package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.traffic.model.MethodFilter
import com.devuloopers.knet.domain.traffic.model.ProtocolFilter
import com.devuloopers.knet.domain.traffic.model.StatusFilter
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.ColumnVisibilityState
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficColumn

/**
 * Immutable State DTO for TrafficFilterBar.
 */
public data class TrafficFilterBarState(
    val selectedProtocol: ProtocolFilter = ProtocolFilter.ALL,
    val selectedMethod: MethodFilter = MethodFilter.ALL,
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    val totalCount: Int = 0,
    val httpCount: Int = 0,
    val httpsCount: Int = 0,
    val wsCount: Int = 0,
    val otherCount: Int = 0,
    val columnVisibility: ColumnVisibilityState = ColumnVisibilityState()
)

/**
 * Action Callbacks DTO for TrafficFilterBar.
 */
public data class TrafficFilterBarActions(
    val onProtocolSelected: (ProtocolFilter) -> Unit = {},
    val onMethodSelected: (MethodFilter) -> Unit = {},
    val onStatusSelected: (StatusFilter) -> Unit = {},
    val onToggleColumn: (TrafficColumn) -> Unit = {}
)

/**
 * Quick Statistics & Dropdown Filters bar bound strictly to :ui:core design tokens and parameter DTOs.
 */
@Composable
public fun TrafficFilterBar(
    state: TrafficFilterBarState,
    actions: TrafficFilterBarActions,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing
    val dimensions = KNetTheme.dimensions

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(themeColors.surface)
            .border(width = 1.dp, color = themeColors.border)
            .padding(horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Group (Count Chips + Dropdowns - Horizontal Scrollable on Window Shrink)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            // "All" Chip
            Text(
                text = "All ${state.totalCount}",
                style = typography.labelMedium.copy(
                    color = if (state.selectedProtocol == ProtocolFilter.ALL) themeColors.accent else themeColors.textSecondary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clickable { actions.onProtocolSelected(ProtocolFilter.ALL) }
                    .handCursor()
                    .padding(horizontal = spacing.xs, vertical = spacing.xs)
            )

            // "HTTP" Chip
            FilterCountChip(
                label = "HTTP",
                count = state.httpCount,
                countColor = themeColors.semantic.success,
                isSelected = state.selectedProtocol == ProtocolFilter.HTTP,
                onClick = { actions.onProtocolSelected(ProtocolFilter.HTTP) }
            )

            // "HTTPS" Chip
            FilterCountChip(
                label = "HTTPS",
                count = state.httpsCount,
                countColor = themeColors.semantic.info,
                isSelected = state.selectedProtocol == ProtocolFilter.HTTPS,
                onClick = { actions.onProtocolSelected(ProtocolFilter.HTTPS) }
            )

            // "WS" Chip
            FilterCountChip(
                label = "WS",
                count = state.wsCount,
                countColor = themeColors.semantic.warning,
                isSelected = state.selectedProtocol == ProtocolFilter.WEBSOCKET,
                onClick = { actions.onProtocolSelected(ProtocolFilter.WEBSOCKET) }
            )

            // "Other" Chip
            FilterCountChip(
                label = "Other",
                count = state.otherCount,
                countColor = themeColors.semantic.error,
                isSelected = state.selectedProtocol == ProtocolFilter.OTHER,
                onClick = { actions.onProtocolSelected(ProtocolFilter.OTHER) }
            )

            // Vertical Divider
            VerticalDivider(
                modifier = Modifier
                    .height(16.dp)
                    .padding(horizontal = spacing.xs),
                color = themeColors.border
            )

            // Dropdowns — KNetDropdown from :ui:core with category placeholder headers
            KNetDropdown(
                placeholder = "Method",
                selectedItem = state.selectedMethod.label,
                items = methodOptions,
                onItemSelected = { selectedLabel ->
                    MethodFilter.entries.find { it.label == selectedLabel }?.let { actions.onMethodSelected(it) }
                }
            )
            KNetDropdown(
                placeholder = "Status",
                selectedItem = state.selectedStatus.label,
                items = statusOptions,
                onItemSelected = { selectedLabel ->
                    StatusFilter.entries.find { it.label == selectedLabel }?.let { actions.onStatusSelected(it) }
                }
            )
            KNetDropdown(
                placeholder = "Protocol",
                selectedItem = state.selectedProtocol.label,
                items = protocolOptions,
                onItemSelected = { selectedLabel ->
                    ProtocolFilter.entries.find { it.label == selectedLabel }?.let { actions.onProtocolSelected(it) }
                }
            )
        }

        // Right Group: Column Toggle Dropdown (Replaces Hamburger Menu)
        ColumnsToggleDropdown(
            columnVisibility = state.columnVisibility,
            onToggleColumn = actions.onToggleColumn
        )
    }
}

@Composable
private fun ColumnsToggleDropdown(
    columnVisibility: ColumnVisibilityState,
    onToggleColumn: (TrafficColumn) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    Box {
        Row(
            modifier = Modifier
                .clip(shapes.small)
                .background(themeColors.surfaceVariant)
                .clickable { expanded = !expanded }
                .padding(horizontal = spacing.sm, vertical = spacing.xs)
                .handCursor(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = "Columns",
                style = typography.labelMedium.copy(color = themeColors.textSecondary)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Columns Dropdown",
                tint = themeColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(themeColors.surface)
        ) {
            TrafficColumn.entries.filter { !it.isMandatory }.forEach { col ->
                ColumnCheckboxItem(
                    label = col.displayName,
                    isChecked = columnVisibility.isVisible(col),
                    onToggle = { onToggleColumn(col) }
                )
            }
        }
    }
}

@Composable
private fun ColumnCheckboxItem(
    label: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = themeColors.accent,
                        uncheckedColor = themeColors.textMuted
                    )
                )
                Text(
                    text = label,
                    style = typography.labelMedium.copy(color = themeColors.textPrimary)
                )
            }
        },
        onClick = { onToggle() }
    )
}

@Composable
private fun FilterCountChip(
    label: String,
    count: Int,
    countColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    Row(
        modifier = Modifier
            .clip(shapes.small)
            .background(if (isSelected) themeColors.border else themeColors.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = spacing.sm, vertical = spacing.xs)
            .handCursor(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(
            text = label,
            style = typography.labelMedium.copy(
                color = themeColors.textSecondary
            )
        )
        Text(
            text = "$count",
            style = typography.labelMedium.copy(
                color = countColor,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/** Filter option datasets for TrafficFilterBar dropdowns. */
private val methodOptions = MethodFilter.entries.map { it.label }
private val statusOptions = StatusFilter.entries.map { it.label }
private val protocolOptions = ProtocolFilter.entries.map { it.label }

