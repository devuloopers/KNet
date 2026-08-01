package com.devuloopers.knet.ui.core.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.icon.KNetIcons
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Compact search bar widget with search icon, clear button, and focus borders.
 *
 * @param query Search string.
 * @param onQueryChange Callback when search query changes.
 * @param modifier Layout modifier.
 * @param placeholder Hint text when empty.
 */
@Composable
public fun WidgetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Filter or search..."
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(KNetColors.FieldDark, KNetShapes.Medium)
            .border(
                width = 1.dp,
                color = if (isFocused) KNetColors.ActiveBlue else KNetColors.BorderDark,
                shape = KNetShapes.Medium
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = KNetIcons.SearchIcon,
            contentDescription = "Search",
            tint = KNetColors.TextMuted,
            modifier = Modifier.size(12.dp).padding(end = 4.dp)
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = KNetColors.TextMuted,
                    fontSize = 10.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { isFocused = it.isFocused },
                singleLine = true,
                cursorBrush = SolidColor(KNetColors.ActiveBlue),
                textStyle = TextStyle(
                    color = KNetColors.TextPrimary,
                    fontSize = 10.sp
                )
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = KNetIcons.ClearIcon,
                contentDescription = "Clear search",
                tint = KNetColors.TextSecondary,
                modifier = Modifier
                    .size(12.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}
