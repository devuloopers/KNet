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
 * Syntax highlighter strategy tailored specifically for XML documents.
 * Handles XML prologs (<?xml ...?>), CDATA (<![CDATA[...]]>), XML comments, elements, attributes, and line folding.
 */
class XmlLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "xml"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        return TagMarkupHighlighter.calculateFoldRanges(lines)
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
                    isXml = true,
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
