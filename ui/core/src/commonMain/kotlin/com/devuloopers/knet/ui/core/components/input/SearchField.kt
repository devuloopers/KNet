package com.devuloopers.knet.ui.core.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Cursor-stable search input composable with prefix search icon, optional inline shortcut badge, and trailing clear button.
 * Delegates to [KNetTextField] for single source of truth and text overflow hover popup capabilities.
 */
@Composable
fun KNetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    shortcutKey: String? = null,
    onClear: (() -> Unit)? = { onQueryChange("") }
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val dimensions = KNetTheme.dimensions

    KNetTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.widthIn(min = dimensions.searchFieldMinWidth),
        config = InputFieldConfig(
            placeholder = placeholder,
            showHoverPopupOnOverflow = true
        ),
        slots = InputFieldSlots(
            prefix = {
                Icon(
                    imageVector = KNetIcons.Search,
                    contentDescription = "Search",
                    modifier = Modifier
                        .size(dimensions.iconSizeSmall)
                        .padding(end = 4.dp),
                    tint = themeColors.textSecondary
                )
            },
            suffix = {
                if (query.isNotEmpty() && onClear != null) {
                    Icon(
                        imageVector = KNetIcons.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(dimensions.iconSizeSmall)
                            .clickable(onClick = onClear)
                            .handCursor(),
                        tint = themeColors.textSecondary
                    )
                } else if (shortcutKey != null) {
                    Box(
                        modifier = Modifier
                            .clip(shapes.small)
                            .background(themeColors.border)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = shortcutKey,
                            style = typography.caption.copy(
                                color = themeColors.textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        )
    )
}
