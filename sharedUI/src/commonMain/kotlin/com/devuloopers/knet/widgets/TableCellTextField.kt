package com.devuloopers.knet.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * A reusable, fixed-height, single-line text field styled for API Studio table cells.
 *
 * Centralises all table cell text field styling in one place so that any future
 * visual changes (font size, color scheme, height) only need to happen here.
 *
 * Features:
 * - Fixed row height via [rowHeight] so rows never shrink or grow while typing.
 * - Vertically centered text and cursor via [contentAlignment = Alignment.CenterStart].
 * - Optional [placeholder] shown as a dim hint when the field is empty.
 * - [textColor] is configurable per call site (e.g. key vs value colouring).
 * - Monospace font family for consistent technical data presentation.
 *
 * @param value The current [TextFieldValue] state.
 * @param onValueChange Callback invoked on every text change.
 * @param modifier Additional [Modifier] applied to the outer [Box] container.
 * @param placeholder Hint text displayed when [value] is empty. Hidden once user types.
 * @param textColor The color applied to the typed text.
 * @param enabled Whether the field accepts input. When false, text is dim and non-interactive.
 * @param rowHeight Fixed height of the cell container. Defaults to [32.dp].
 * @param fontWeight Font weight for the typed text. Defaults to [FontWeight.Normal].
 */
@Composable
fun TableCellTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textColor: Color = KNetColors.ActiveBlue,
    enabled: Boolean = true,
    rowHeight: Dp = 32.dp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Box(
        modifier = modifier.height(rowHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            textStyle = TextStyle(
                color = if (enabled) textColor else KNetColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = fontWeight
            ),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = KNetColors.TextSecondary.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = fontWeight
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
