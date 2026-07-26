package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * A reusable, high-performance search input bar for KNet widgets.
 * Manages cursor position locally to prevent cursor reset issues in reactive state flows.
 *
 * @param query Current active search string query.
 * @param onQueryChange Callback invoked when the user types or clears the query.
 * @param placeholder Optional placeholder text displayed when query is empty.
 * @param modifier Layout modifier for custom styling.
 */
@Composable
fun WidgetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Filter...",
    modifier: Modifier = Modifier
) {
    var tfValue by remember { mutableStateOf(TextFieldValue(text = query)) }

    LaunchedEffect(query) {
        if (query != tfValue.text) {
            tfValue = tfValue.copy(
                text = query,
                selection = TextRange(query.length)
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.FieldDark, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = KNetColors.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = tfValue,
            onValueChange = { newValue ->
                tfValue = newValue
                onQueryChange(newValue.text)
            },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = KNetColors.TextSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear Search",
                tint = KNetColors.TextSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .clickable {
                        tfValue = TextFieldValue("")
                        onQueryChange("")
                    }
            )
        }
    }
}
