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
import com.devuloopers.knet.theme.KNetColors
import java.util.ArrayDeque

/**
 * Syntax highlighter strategy for JavaScript code.
 */
class JsLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "javascript"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val foldRanges = mutableMapOf<Int, Int>()
        val stack = ArrayDeque<Int>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.endsWith("{") || trimmed.endsWith("[")) {
                stack.push(index)
            } else if (trimmed.startsWith("}") || trimmed.startsWith("]")) {
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
        return when {
            endLineTrimmed.startsWith("}") -> "} "
            endLineTrimmed.startsWith("]") -> "] "
            else -> ""
        }
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
                    val keywords = setOf(
                        "const", "let", "var", "function", "class", "return", "if", "else",
                        "for", "while", "import", "export", "from", "default", "new", "this",
                        "true", "false", "null", "undefined", "async", "await", "try", "catch", "throw"
                    )

                    var i = 0
                    var tokenBuilder = StringBuilder()

                    while (i < lineText.length) {
                        val ch = lineText[i]

                        // Comments
                        if (ch == '/' && i + 1 < lineText.length) {
                            if (lineText[i + 1] == '/') {
                                withStyle(SpanStyle(color = Color(0xFF7A8A99))) {
                                    append(lineText.substring(i))
                                }
                                break
                            } else if (lineText[i + 1] == '*') {
                                val endOfComment = lineText.indexOf("*/", i + 2)
                                val commentStr = if (endOfComment != -1) {
                                    lineText.substring(i, endOfComment + 2)
                                } else {
                                    lineText.substring(i)
                                }
                                withStyle(SpanStyle(color = Color(0xFF7A8A99))) {
                                    append(commentStr)
                                }
                                i += commentStr.length
                                continue
                            }
                        }

                        // Strings
                        if (ch == '\'' || ch == '"' || ch == '`') {
                            val quoteType = ch
                            val stringBuilder = StringBuilder().append(ch)
                            var escaped = false
                            var stringEndIndex = -1
                            for (j in (i + 1) until lineText.length) {
                                val cur = lineText[j]
                                stringBuilder.append(cur)
                                if (escaped) {
                                    escaped = false
                                } else if (cur == '\\') {
                                    escaped = true
                                } else if (cur == quoteType) {
                                    stringEndIndex = j
                                    break
                                }
                            }
                            withStyle(SpanStyle(color = Color(0xFF80B680))) {
                                append(stringBuilder.toString())
                            }
                            i += stringBuilder.length
                            continue
                        }

                        // Word token checking (keywords and variable types)
                        if (ch.isLetterOrDigit() || ch == '_' || ch == '$') {
                            tokenBuilder.append(ch)
                        } else {
                            if (tokenBuilder.isNotEmpty()) {
                                val token = tokenBuilder.toString()
                                tokenBuilder.setLength(0)

                                if (token in keywords) {
                                    withStyle(SpanStyle(color = Color(0xFFDE935F), fontWeight = FontWeight.Bold)) {
                                        append(token)
                                    }
                                } else if (token.toDoubleOrNull() != null) {
                                    withStyle(SpanStyle(color = Color(0xFF81A2BE))) {
                                        append(token)
                                    }
                                } else {
                                    append(token)
                                }
                            }
                            append(ch)
                        }
                        i++
                    }

                    if (tokenBuilder.isNotEmpty()) {
                        val token = tokenBuilder.toString()
                        if (token in keywords) {
                            withStyle(SpanStyle(color = Color(0xFFDE935F), fontWeight = FontWeight.Bold)) {
                                append(token)
                            }
                        } else if (token.toDoubleOrNull() != null) {
                            withStyle(SpanStyle(color = Color(0xFF81A2BE))) {
                                append(token)
                            }
                        } else {
                            append(token)
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
