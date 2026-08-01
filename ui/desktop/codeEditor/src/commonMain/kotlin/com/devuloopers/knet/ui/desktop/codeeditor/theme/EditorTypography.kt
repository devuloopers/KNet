package com.devuloopers.knet.ui.desktop.codeeditor.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

/**
 * Shared typography styles for code text rendering.
 */
object EditorTypography {
    fun editorTextStyle(): TextStyle = TextStyle(
        fontSize = EditorTokens.FontSize,
        lineHeight = EditorTokens.LineHeight,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Top,
            trim = LineHeightStyle.Trim.None
        )
    )
}
