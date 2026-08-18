package com.devuloopers.knet.ui.core.components.dropdown

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.InputFieldSlots
import com.devuloopers.knet.ui.core.components.input.InputFieldState
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Shared design tokens and dimensional constants for KNet Dropdown components.
 */
object KNetDropdownDefaults {
    val FieldHeight: Dp = 40.dp
    val ItemHeight: Dp = 40.dp
    val HorizontalPadding: Dp = 12.dp
    val MaxMenuHeight: Dp = 240.dp
}

/**
 * Standardized Desktop Searchable Dropdown (Combobox) Selection Component primitive.
 *
 * Combines [KNetTextField] text input with real-time popup filtering, keyboard navigation,
 * and hand cursor hover feedback. Anchor popup width matches the exact width of the input field.
 *
 * ### UX Contract & Focus Rules:
 * - **Focus Preservation**: Popup is rendered with `PopupProperties(focusable = false)` so the popup
 *   never steals keyboard focus from [KNetTextField]. Typing, backspace, and arrow keys continue
 *   working without focus loss.
 * - **Typing to Filter**: Typing automatically opens the dropdown and filters results in real time.
 * - **Trailing Icon Toggle**: Clicking the trailing arrow toggles the dropdown (shows full list on open).
 * - **Keyboard Navigation**: `ArrowDown` / `ArrowUp` move highlight index; `Enter` selects highlighted item; `Escape` closes popup.
 * - **Dismissal**: Clicking outside or pressing `Escape` restores the previously selected item text.
 * - **Dimensional Rhythm**: Field height (`40.dp`), item height (`40.dp`), and horizontal padding (`12.dp`)
 *   use shared [KNetDropdownDefaults] tokens for exact visual alignment.
 *
 * @param selectedItem Currently selected item value of type [T], or null if unselected.
 * @param items List of available selection items.
 * @param onItemSelected Callback fired when an item is selected from the popup.
 * @param modifier Layout modifier for custom sizing or positioning.
 * @param placeholder Default hint string shown when unselected (defaults to "Select...").
 * @param enabled Whether the dropdown trigger is interactive.
 * @param itemText Selector function transforming item [T] into display / filter text.
 * @param itemContent Optional custom composable rendering for dropdown items.
 */
@Composable
fun <T> KNetSearchableDropdown(
    selectedItem: T?,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select...",
    enabled: Boolean = true,
    itemText: (T) -> String = { it.toString() },
    itemContent: (@Composable (T) -> Unit)? = null
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val density = LocalDensity.current

    var expanded by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var searchQuery by remember { mutableStateOf("") }
    var highlightedIndex by remember { mutableStateOf(-1) }

    // Display text in input field:
    // When expanded is true, shows active searchQuery for live typing & filtering.
    // When expanded is false, shows the selected item's formatted text.
    val currentFieldValue = if (expanded) {
        searchQuery
    } else {
        if (selectedItem != null) itemText(selectedItem) else ""
    }

    // Filter items based on active searchQuery (case-insensitive)
    val filteredItems = remember(items, searchQuery, itemText) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            items
        } else {
            items.filter { itemText(it).contains(query, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { containerSize = it }
    ) {
        KNetTextField(
            value = currentFieldValue,
            onValueChange = { newValue: String ->
                if (!expanded) {
                    expanded = true
                }
                searchQuery = newValue
                highlightedIndex = -1
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(KNetDropdownDefaults.FieldHeight)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionDown -> {
                                if (!expanded) {
                                    expanded = true
                                    searchQuery = ""
                                    highlightedIndex = -1
                                } else if (filteredItems.isNotEmpty()) {
                                    highlightedIndex = (highlightedIndex + 1) % filteredItems.size
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (expanded && filteredItems.isNotEmpty()) {
                                    highlightedIndex = if (highlightedIndex > 0) {
                                        highlightedIndex - 1
                                    } else {
                                        filteredItems.size - 1
                                    }
                                }
                                true
                            }
                            Key.Enter -> {
                                if (expanded && highlightedIndex in filteredItems.indices) {
                                    val item = filteredItems[highlightedIndex]
                                    onItemSelected(item)
                                    expanded = false
                                    searchQuery = ""
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.Escape -> {
                                if (expanded) {
                                    expanded = false
                                    searchQuery = ""
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .handCursor(),
            config = InputFieldConfig(
                placeholder = placeholder,
                showHoverPopupOnOverflow = false
            ),
            state = InputFieldState(enabled = enabled),
            slots = InputFieldSlots(
                suffix = {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) "Collapse dropdown" else "Expand dropdown",
                        tint = themeColors.textSecondary.copy(
                            alpha = if (enabled) 1f else 0.38f
                        ),
                        modifier = Modifier
                            .clickable(enabled = enabled) {
                                expanded = !expanded
                                if (expanded) {
                                    searchQuery = ""
                                    highlightedIndex = -1
                                }
                            }
                            .handCursor()
                    )
                }
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            },
            // CRITICAL: focusable = false prevents popup from stealing keyboard focus from text field!
            properties = PopupProperties(focusable = false),
            modifier = Modifier
                .width(
                    if (containerSize.width > 0) {
                        with(density) { containerSize.width.toDp() }
                    } else {
                        200.dp
                    }
                )
                .heightIn(max = KNetDropdownDefaults.MaxMenuHeight)
                .background(themeColors.surfaceVariant)
                .border(1.dp, themeColors.border, shapes.medium)
        ) {
            if (filteredItems.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "No results found",
                            style = typography.bodySmall,
                            color = themeColors.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = KNetDropdownDefaults.HorizontalPadding)
                        )
                    },
                    onClick = {
                        expanded = false
                        searchQuery = ""
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KNetDropdownDefaults.ItemHeight)
                )
            } else {
                filteredItems.forEachIndexed { index, item ->
                    val isSelected = item == selectedItem
                    val isHighlighted = index == highlightedIndex

                    val rowBackground = when {
                        isSelected -> themeColors.interaction.selectedOverlay
                        isHighlighted -> themeColors.interaction.hoverOverlay
                        else -> Color.Transparent
                    }

                    DropdownMenuItem(
                        text = {
                            if (itemContent != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = KNetDropdownDefaults.HorizontalPadding)
                                ) {
                                    itemContent(item)
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = KNetDropdownDefaults.HorizontalPadding),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = itemText(item),
                                        style = typography.bodySmall.copy(
                                            color = if (isSelected || isHighlighted) themeColors.accent else themeColors.textPrimary,
                                            fontWeight = if (isSelected || isHighlighted) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onItemSelected(item)
                            searchQuery = ""
                            expanded = false
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KNetDropdownDefaults.ItemHeight)
                            .background(rowBackground)
                            .handCursor()
                    )
                }
            }
        }
    }
}

/**
 * Compose Preview for [KNetSearchableDropdown] with sample data.
 */
@Preview
@Composable
fun KNetSearchableDropdownPreview() {
    val sampleItems = listOf(
        "badssl.com",
        "client.badssl.com",
        "self-signed.badssl.com",
        "untrusted-root.badssl.com",
        "expired.badssl.com",
        "wrong.host.badssl.com",
        "dh2048.badssl.com",
        "hsts.badssl.com",
        "upgrade.badssl.com",
        "preloaded.badssl.com"
    )
    var selected by remember { mutableStateOf<String?>(sampleItems.first()) }

    KNetTheme {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .width(320.dp)
        ) {
            KNetSearchableDropdown(
                selectedItem = selected,
                items = sampleItems,
                onItemSelected = { selected = it },
                placeholder = "Select host rule..."
            )
        }
    }
}
