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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Reusable dark input field component for KNet forms, search bars, and URL inputs.
 *
 * @param value The current string value of the input field.
 * @param onValueChange Callback triggered when text content changes.
 * @param modifier Layout modifier.
 * @param placeholder Hint text when empty.
 * @param height Default field height.
 * @param isMonospace Whether to apply a monospace font family.
 * @param singleLine Whether to constrain input to a single line.
 */
@Composable
public fun KNetInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    height: Dp = 28.dp,
    isMonospace: Boolean = true,
    singleLine: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(height)
            .background(KNetColors.FieldDark, KNetShapes.Medium)
            .border(
                width = 1.dp,
                color = if (isFocused) KNetColors.ActiveBlue else KNetColors.BorderDark,
                shape = KNetShapes.Medium
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                color = KNetColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = singleLine,
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            textStyle = TextStyle(
                color = KNetColors.TextPrimary,
                fontSize = 11.sp,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
            )
        )
    }
}
