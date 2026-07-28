package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Reusable Code Editor Component for scripts (Pre-request Script, Test Scripts, Request Body).
 *
 * Provides line numbering perfectly aligned 1:1 with input text, placeholder hint support,
 * dark editor container styling `#0D1117`, active cursor, and monospace font typography.
 *
 * @param code The current script or code text string.
 * @param onCodeChange Callback triggered when the code is edited.
 * @param modifier Resizing constraints.
 * @param placeholder Dim hint displayed when [code] is empty.
 * @param textColor Main text color for typed code. Defaults to purple `0xFFA855F7`.
 */
@Composable
fun CodeEditorWidget(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textColor: Color = Color(0xFFA855F7)
) {
    var textFieldValue by remember(code) {
        mutableStateOf(
            TextFieldValue(
                text = code,
                selection = androidx.compose.ui.text.TextRange(code.length)
            )
        )
    }

    val lines = textFieldValue.text.lines()
    val lineCount = lines.size.coerceAtLeast(1)

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
            .border(1.dp, KNetColors.BorderDark.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        // Line Numbers Gutter — matching top padding & line height of editor
        Column(
            modifier = Modifier
                .padding(top = 2.dp, end = 12.dp)
                .width(28.dp),
            horizontalAlignment = Alignment.End
        ) {
            (1..lineCount).forEach { num ->
                Text(
                    text = num.toString(),
                    color = Color(0xFF484F58),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                    style = TextStyle(
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                        )
                    ),
                    textAlign = TextAlign.End
                )
            }
        }

        // Code Input Area — with decorationBox for placeholder hint
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onCodeChange(newValue.text)
            },
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            textStyle = TextStyle(
                color = textColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                )
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.TopStart
                ) {
                    if (textFieldValue.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = KNetColors.TextSecondary.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp,
                            style = TextStyle(
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                                )
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
