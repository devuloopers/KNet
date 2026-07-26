package com.devuloopers.knet.highlighter

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.util.*

/**
 * Syntax highlighter strategy for CSS styles.
 */
class CssLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "css"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val foldRanges = mutableMapOf<Int, Int>()
        val stack = ArrayDeque<Int>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.endsWith("{")) {
                stack.push(index)
            } else if (trimmed.startsWith("}")) {
                if (stack.isNotEmpty()) {
                    val start = stack.pop()
                    foldRanges[start] = index
                }
            }
        }
        return foldRanges
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex < 0 || endLineIndex >= lines.size) return ""
        val endLineTrimmed = lines[endLineIndex].trim()
        return if (endLineTrimmed.startsWith("}")) "} " else ""
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
            val annotated = remember(lineText) {
                buildAnnotatedString {
                    val trimmed = lineText.trim()

                    // Handle comments
                    if (trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.endsWith("*/")) {
                        withStyle(SpanStyle(color = Color(0xFF7A8A99))) {
                            append(lineText)
                        }
                        return@buildAnnotatedString
                    }

                    // Format rules vs selectors
                    if (lineText.contains(":") && !lineText.contains("{") && !lineText.contains("}")) {
                        // This is a property declaration (e.g. margin-left: 20px;)
                        val parts = lineText.split(":", limit = 2)
                        val prop = parts[0]
                        val value = parts.getOrNull(1) ?: ""

                        withStyle(SpanStyle(color = Color(0xFF81A2BE))) { // Property color
                            append(prop)
                        }
                        append(":")
                        withStyle(SpanStyle(color = Color(0xFF80B680))) { // Value color
                            append(value)
                        }
                    } else {
                        // This is a selector block (e.g. .class-selector, #id-selector)
                        var i = 0
                        while (i < lineText.length) {
                            when (val ch = lineText[i]) {
                                '.', '#' -> {
                                    withStyle(SpanStyle(color = Color(0xFFDE935F), fontWeight = FontWeight.Bold)) {
                                        append(ch)
                                    }
                                }

                                '{', '}' -> {
                                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                        append(ch)
                                    }
                                }

                                else -> {
                                    if (ch.isLetterOrDigit() || ch == '-' || ch == '_') {
                                        withStyle(SpanStyle(color = Color(0xFFCC6666))) {
                                            append(ch)
                                        }
                                    } else {
                                        append(ch)
                                    }
                                }
                            }
                            i++
                        }
                    }
                }
            }

            Text(
                text = annotated,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                softWrap = true,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (isCollapsed) {
                CollapsedBadge(closingSymbol = closingSymbol, onToggleFold = onToggleFold)
            }
        }
    }
}
