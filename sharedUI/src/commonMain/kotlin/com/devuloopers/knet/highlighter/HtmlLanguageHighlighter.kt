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
 * Syntax highlighter strategy for HTML and XML documents.
 */
class HtmlLanguageHighlighter(
    override val languageId: String = "html"
) : CodeLanguageHighlighter {

    private val voidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        return TagMarkupHighlighter.calculateFoldRanges(lines, voidTags)
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex < 0 || endLineIndex >= lines.size) return ""
        val endLineTrimmed = lines[endLineIndex].trim()
        return if (endLineTrimmed.startsWith("</")) "$endLineTrimmed " else ""
    }

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
            if (lineText.contains("<") && lineText.contains(">")) {
                TagMarkupLineText(
                    lineText = lineText,
                    isXml = false,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Text(
                    text = lineText,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    softWrap = true,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            if (isCollapsed) {
                CollapsedBadge(closingSymbol = closingSymbol, onToggleFold = onToggleFold)
            }
        }
    }
}
