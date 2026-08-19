package com.devuloopers.knet.ui.core.components.dropdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.foundation.pointer.textCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Searchable KNet combobox with anchored results and keyboard navigation.
 *
 * @param selectedItem Current value, or null when no value is selected.
 * @param items Values available for filtering.
 * @param onItemSelected Invoked when a result is chosen.
 * @param modifier Modifier applied to the combobox anchor; sizing modifiers override the compact default width.
 * @param placeholder Hint and accessible field label.
 * @param enabled Whether the combobox accepts input.
 * @param itemText Converts a value into searchable display text.
 * @param itemContent Optional custom result rendering.
 */
@Composable
fun <T> KNetSearchableDropdown(
    selectedItem: T?,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select…",
    enabled: Boolean = true,
    itemText: (T) -> String = { it.toString() },
    itemContent: (@Composable (T) -> Unit)? = null
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focused by interactionSource.collectIsFocusedAsState()
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var highlightedIndex by remember { mutableStateOf(-1) }

    val visibleText = if (expanded) query else selectedItem?.let(itemText).orEmpty()
    val filteredItems = remember(items, query, itemText) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) items else items.filter { itemText(it).contains(normalizedQuery, ignoreCase = true) }
    }
    val borderColor = if (focused || expanded) colors.borderFocused else colors.border

    fun close() {
        expanded = false
        query = ""
        highlightedIndex = -1
    }

    Box(
        modifier = modifier
            .width(KNetDropdownDefaults.DefaultWidth)
            .onSizeChanged { anchorWidthPx = it.width }
    ) {
        BasicTextField(
            value = visibleText,
            onValueChange = { value ->
                if (!expanded) expanded = true
                query = value
                highlightedIndex = -1
            },
            enabled = enabled,
            singleLine = true,
            textStyle = typography.bodySmall.copy(color = if (enabled) colors.textPrimary else colors.textMuted),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(KNetDropdownDefaults.FieldHeight)
                .clip(shapes.medium)
                .background(colors.surfaceVariant)
                .border(1.dp, borderColor, shapes.medium)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !enabled) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (!expanded) expanded = true
                            if (filteredItems.isNotEmpty()) highlightedIndex = (highlightedIndex + 1).mod(filteredItems.size)
                            true
                        }
                        Key.DirectionUp -> {
                            if (!expanded) expanded = true
                            if (filteredItems.isNotEmpty()) highlightedIndex = (highlightedIndex - 1).mod(filteredItems.size)
                            true
                        }
                        Key.Enter -> {
                            if (highlightedIndex in filteredItems.indices) {
                                onItemSelected(filteredItems[highlightedIndex])
                                close()
                                true
                            } else false
                        }
                        Key.Escape -> {
                            if (expanded) {
                                close()
                                true
                            } else false
                        }
                        else -> false
                    }
                }
                .semantics {
                    contentDescription = placeholder
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .textCursor(),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = KNetDropdownDefaults.HorizontalPadding),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (visibleText.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.textMuted,
                                style = typography.bodySmall,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    KNetIconButton(
                        icon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse options" else "Expand options",
                        onClick = {
                            if (expanded) close() else {
                                focusRequester.requestFocus()
                                expanded = true
                                query = ""
                                highlightedIndex = -1
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.size(30.dp),
                        size = 30.dp,
                        iconSize = 17.dp,
                        tint = if (enabled) colors.textSecondary else colors.textMuted
                    )
                }
            }
        )

        KNetDropdownPopup(
            expanded = expanded,
            onDismissRequest = ::close,
            anchorWidthPx = anchorWidthPx,
            focusable = false
        ) {
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KNetDropdownDefaults.ItemHeight)
                        .padding(horizontal = KNetDropdownDefaults.HorizontalPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = "No matching options", color = colors.textMuted, style = typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = KNetDropdownDefaults.MaxMenuHeight)
                ) {
                    itemsIndexed(
                        items = filteredItems,
                        key = { index, item -> "${itemText(item)}-$index" }
                    ) { index, item ->
                        KNetDropdownMenuItem(
                            text = itemText(item),
                            selected = item == selectedItem,
                            highlighted = index == highlightedIndex,
                            onClick = {
                                onItemSelected(item)
                                close()
                            },
                            content = itemContent?.let { renderer -> { renderer(item) } }
                        )
                    }
                }
            }
        }
    }
}
