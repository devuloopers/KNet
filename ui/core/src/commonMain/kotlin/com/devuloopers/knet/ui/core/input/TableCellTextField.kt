package com.devuloopers.knet.ui.core.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Compact inline text field editor for table cells (headers, parameters, environment variables).
 *
 * @param value Cell text value.
 * @param onValueChange Callback when cell text changes.
 * @param modifier Layout parameters.
 * @param placeholder Hint text when empty.
 */
@Composable
public fun TableCellTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(24.dp)
            .background(KNetColors.FieldDark, KNetShapes.Small)
            .border(
                width = 1.dp,
                color = if (isFocused) KNetColors.ActiveBlue else KNetColors.BorderDark,
                shape = KNetShapes.Small
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                color = KNetColors.TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = true,
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            textStyle = TextStyle(
                color = KNetColors.TextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}
