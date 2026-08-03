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
 * Standardized Desktop Dropdown Selection Component primitive.
 * Displays a high-density IDE selection control and popup menu styled strictly with :ui:core tokens.
 *
 * @param placeholder Header label displayed when default/all state is active (e.g., "Method", "Status", "Protocol").
 * @param defaultItem The item representing the unselected/all state (defaults to "ALL" or items.firstOrNull()).
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
    itemText: (T) -> String = { it.toString() }
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
                    color = if (isDefaultSelected) themeColors.textSecondary else themeColors.textPrimary,
                    fontWeight = FontWeight.Medium
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
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemText(item),
                            style = typography.bodySmall.copy(
                                color = if (isSelected) themeColors.accent else themeColors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
