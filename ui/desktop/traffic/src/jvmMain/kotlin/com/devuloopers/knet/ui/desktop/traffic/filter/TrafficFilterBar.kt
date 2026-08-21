package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.desktop.traffic.model.MethodFilter
import com.devuloopers.knet.ui.desktop.traffic.model.ProtocolFilter
import com.devuloopers.knet.ui.desktop.traffic.model.StatusFilter
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownDefaults
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownSize
import com.devuloopers.knet.ui.core.components.dropdown.KNetMultiSelectDropdown
import com.devuloopers.knet.ui.core.components.dropdown.KNetMultiSelectAction
import com.devuloopers.knet.ui.core.components.input.KNetSearchField
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.desktop.httppanel.theme.HttpMethodColors
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.ColumnVisibilityState
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficColumn

/**
 * Immutable State DTO for TrafficFilterBar.
 */
data class TrafficFilterBarState(
    val searchQuery: String = "",
    val selectedProtocol: ProtocolFilter = ProtocolFilter.ALL,
    val selectedMethod: MethodFilter = MethodFilter.ALL,
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    val totalCount: Long = 0L,
    val httpCount: Int = 0,
    val httpsCount: Int = 0,
    val http2Count: Int = 0,
    val columnVisibility: ColumnVisibilityState = ColumnVisibilityState()
)

/**
 * Interaction callbacks for [TrafficFilterBar].
 *
 * @property onSearchChange Updates the retained-traffic search query.
 * @property onProtocolSelected Selects the scheme or application-protocol filter.
 * @property onMethodSelected Selects the canonical HTTP method filter.
 * @property onStatusSelected Selects the HTTP status-family filter.
 * @property onToggleColumn Toggles one optional typed Traffic column.
 * @property onResetColumnWidths Restores every Traffic column to its default sizing mode.
 */
data class TrafficFilterBarActions(
    val onSearchChange: (String) -> Unit = {},
    val onProtocolSelected: (ProtocolFilter) -> Unit = {},
    val onMethodSelected: (MethodFilter) -> Unit = {},
    val onStatusSelected: (StatusFilter) -> Unit = {},
    val onToggleColumn: (TrafficColumn) -> Unit = {},
    val onResetColumnWidths: () -> Unit = {},
)

/**
 * Quick Statistics & Dropdown Filters bar bound strictly to :ui:core design tokens and parameter DTOs.
 */
@Composable
fun TrafficFilterBar(
    state: TrafficFilterBarState,
    actions: TrafficFilterBarActions,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

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
        // Search and filters share one horizontally scrollable group on constrained windows.
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            KNetSearchField(
                query = state.searchQuery,
                onQueryChange = actions.onSearchChange,
                placeholder = "Search path, host, method...",
                modifier = Modifier.width(240.dp),
            )

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = spacing.xs),
                color = themeColors.border,
            )

            FilterCountChip(
                label = "All",
                count = state.totalCount,
                countColor = if (state.selectedProtocol == ProtocolFilter.ALL) {
                    themeColors.accent
                } else {
                    themeColors.textSecondary
                },
                isSelected = state.selectedProtocol == ProtocolFilter.ALL,
                onClick = { actions.onProtocolSelected(ProtocolFilter.ALL) }
            )

            // "HTTP" Chip
            FilterCountChip(
                label = "HTTP",
                count = state.httpCount.toLong(),
                countColor = themeColors.semantic.success,
                isSelected = state.selectedProtocol == ProtocolFilter.HTTP,
                onClick = { actions.onProtocolSelected(ProtocolFilter.HTTP) }
            )

            // "HTTPS" Chip
            FilterCountChip(
                label = "HTTPS",
                count = state.httpsCount.toLong(),
                countColor = themeColors.semantic.info,
                isSelected = state.selectedProtocol == ProtocolFilter.HTTPS,
                onClick = { actions.onProtocolSelected(ProtocolFilter.HTTPS) }
            )

            // HTTP/2 is an application-protocol filter and intentionally separate from HTTPS.
            FilterCountChip(
                label = "HTTP/2",
                count = state.http2Count.toLong(),
                countColor = themeColors.semantic.warning,
                isSelected = state.selectedProtocol == ProtocolFilter.HTTP_2,
                onClick = { actions.onProtocolSelected(ProtocolFilter.HTTP_2) }
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
                selectedItem = state.selectedMethod,
                items = MethodFilter.entries,
                onItemSelected = actions.onMethodSelected,
                size = KNetDropdownSize.Compact,
                itemText = MethodFilter::label,
                itemColor = { method -> HttpMethodColors.getMethodTextColor(method.label) }
            )
            KNetDropdown(
                placeholder = "Status",
                selectedItem = state.selectedStatus,
                items = StatusFilter.entries,
                onItemSelected = actions.onStatusSelected,
                size = KNetDropdownSize.Compact,
                itemText = StatusFilter::label
            )
            KNetDropdown(
                placeholder = "Protocol",
                selectedItem = state.selectedProtocol,
                items = ProtocolFilter.entries,
                onItemSelected = actions.onProtocolSelected,
                size = KNetDropdownSize.Compact,
                itemText = ProtocolFilter::label
            )
        }

        KNetMultiSelectDropdown(
            label = "Columns",
            items = toggleableTrafficColumns,
            isItemSelected = state.columnVisibility::isVisible,
            onItemToggle = actions.onToggleColumn,
            size = KNetDropdownSize.Compact,
            itemText = TrafficColumn::displayName,
            footerAction = KNetMultiSelectAction(
                label = "Reset column widths",
                onClick = actions.onResetColumnWidths,
            ),
        )
    }
}

@Composable
private fun FilterCountChip(
    label: String,
    count: Long,
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
            .height(KNetDropdownDefaults.CompactFieldHeight)
            .clip(shapes.small)
            .background(if (isSelected) themeColors.border else themeColors.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = spacing.sm)
            .handCursor(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(
            text = label,
            style = typography.labelMedium.copy(
                color = themeColors.textSecondary
            ),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "$count",
            style = typography.labelMedium.copy(
                color = countColor,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Optional Traffic columns presented by the shared multi-select dropdown. */
private val toggleableTrafficColumns = TrafficColumn.entries.filterNot(TrafficColumn::isMandatory)
