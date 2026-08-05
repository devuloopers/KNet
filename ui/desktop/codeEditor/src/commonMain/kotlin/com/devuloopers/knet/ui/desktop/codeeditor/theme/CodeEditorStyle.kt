package com.devuloopers.knet.ui.desktop.codeeditor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

/**
 * Visual style configuration object for [com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor] typography and metrics.
 *
 * @property fontSize Monospace font size for code text and line numbers.
 * @property lineHeight Vertical line height for text rows.
 * @property backgroundColor Background color of the editor container.
 */
public data class CodeEditorStyle(
    val fontSize: TextUnit = CodeEditorTokens.FontSize,
    val lineHeight: TextUnit = CodeEditorTokens.LineHeight,
    val backgroundColor: Color = Color(0xFF0D1117)
)
