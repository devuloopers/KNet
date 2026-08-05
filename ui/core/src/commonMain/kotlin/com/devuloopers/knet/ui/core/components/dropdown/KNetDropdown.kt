package com.devuloopers.knet.ui.core.components.dropdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Cohesive data model for colorful dropdown menu options.
 *
 * @property label The display text/name of the dropdown option (e.g. "GET", "POST", "JSON").
 * @property color Optional text color tag for the option. Defaults to [Color.Unspecified].
 */
@Immutable
public data class KNetDropdownOption(
    val label: String,
    val color: Color = Color.Unspecified
)

/**
 * Standardized Desktop Dropdown Selection Component primitive.
 * Displays a high-density IDE selection control and popup menu styled strictly with :ui:core tokens.
 * Supports custom per-item colors for HTTP method tags and category badges.
 *
 * @param selectedItem Currently selected item value.
 * @param items List of available selection items.
 * @param onItemSelected Callback when an item is selected.
 * @param placeholder Header label displayed when default/all state is active (e.g., "Method", "Status", "Protocol").
 * @param defaultItem The item representing the unselected/all state.
 * @param enabled Whether the dropdown trigger is interactive.
 * @param itemText Selector function transforming item [T] into display String text.
 * @param itemColor Optional selector function resolving text [Color] for item [T].
 */
@Composable
public fun <T> KNetDropdown(
    selectedItem: T,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    defaultItem: T? = items.firstOrNull(),
    enabled: Boolean = true,
    itemText: (T) -> String = { it.toString() },
    itemColor: ((T) -> Color?)? = null
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    var expanded by remember { mutableStateOf(false) }

    val isDefaultSelected = selectedItem == defaultItem ||
        (selectedItem is String && selectedItem.equals("ALL", ignoreCase = true))

    val displayText = if (isDefaultSelected && !placeholder.isNullOrEmpty()) {
        placeholder
    } else {
        itemText(selectedItem)
    }

    val selectedTextColor = remember(selectedItem, isDefaultSelected, itemColor) {
        if (isDefaultSelected) {
            themeColors.textSecondary
        } else {
            val customColor = itemColor?.invoke(selectedItem)
            if (customColor != null && customColor != Color.Unspecified) customColor else themeColors.textPrimary
        }
    }

    Box(modifier = modifier) {
        // Dropdown Trigger Button [ Placeholder or Selected Item ▼ ]
        Row(
            modifier = Modifier
                .clip(shapes.small)
                .background(themeColors.surfaceVariant)
                .border(1.dp, themeColors.border, shapes.small)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = spacing.sm, vertical = spacing.xs)
                .handCursor(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = displayText,
                style = typography.labelMedium.copy(
                    color = selectedTextColor,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = themeColors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }

        // Popup Dropdown Menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(themeColors.surfaceVariant)
                .border(1.dp, themeColors.border, shapes.small)
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val customColor = itemColor?.invoke(item)
                val resolvedColor = if (customColor != null && customColor != Color.Unspecified) {
                    customColor
                } else if (isSelected) {
                    themeColors.accent
                } else {
                    themeColors.textPrimary
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemText(item),
                            style = typography.bodySmall.copy(
                                color = resolvedColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                    modifier = Modifier
                        .background(if (isSelected) themeColors.interaction.selectedOverlay else Color.Transparent)
                        .handCursor()
                )
            }
        }
    }
}

/**
 * Convenient overload for lists of [KNetDropdownOption].
 */
@Composable
public fun KNetDropdownOptions(
    selectedOption: KNetDropdownOption,
    options: List<KNetDropdownOption>,
    onOptionSelected: (KNetDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true
) {
    KNetDropdown(
        selectedItem = selectedOption,
        items = options,
        onItemSelected = onOptionSelected,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        itemText = { it.label },
        itemColor = { it.color }
    )
}
