package com.devuloopers.knet.highlighter

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Fallback highlighter strategy for plain text, raw logs, and unformatted content.
 */
class PlainTextLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "plain"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> = emptyMap()

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String = ""

    @Composable
    override fun RenderLineContent(
        lineNumber: Int,
        lineText: String,
        isFoldable: Boolean,
        isCollapsed: Boolean,
        closingSymbol: String,
        onToggleFold: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = lineText,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                softWrap = true,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}
