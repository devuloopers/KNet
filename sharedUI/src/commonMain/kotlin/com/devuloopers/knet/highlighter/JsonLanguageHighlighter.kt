package com.devuloopers.knet.highlighter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors
import java.util.ArrayDeque

data class ParsedJsonKeyValue(
    val leadingIndent: String,
    val keyPart: String,
    val valPart: String
)

/**
 * Syntax highlighter strategy for JSON documents.
 */
class JsonLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "json"

    /**
     * Robustly parses a JSON line into leading indent, keyPart (with quotes), and valPart.
     * Correctly handles keys containing colons (e.g. "google:entityinfo": "val").
     */
    fun parseJsonKeyValueLine(lineText: String): ParsedJsonKeyValue? {
        val leadingIndent = lineText.takeWhile { it.isWhitespace() }
        val trimmed = lineText.substring(leadingIndent.length)
        if (!trimmed.startsWith("\"")) return null

        var inKey = false
        var escaped = false
        var keyEndIndex = -1

        for (i in leadingIndent.length until lineText.length) {
            val ch = lineText[i]
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                if (!inKey) {
                    inKey = true
                } else {
                    keyEndIndex = i
                    break
                }
            }
        }
        if (keyEndIndex == -1) return null

        var colonIndex = -1
        for (i in (keyEndIndex + 1) until lineText.length) {
            if (lineText[i] == ':') {
                colonIndex = i
                break
            }
        }
        if (colonIndex == -1) return null

        val keyPart = lineText.substring(0, keyEndIndex + 1)
        val valPart = lineText.substring(colonIndex + 1)
        return ParsedJsonKeyValue(leadingIndent, keyPart, valPart)
    }

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val foldRanges = mutableMapOf<Int, Int>()
        val stack = ArrayDeque<Pair<Int, Char>>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            var inString = false
            var escaped = false

            for (ch in trimmed) {
                when {
                    escaped -> escaped = false
                    ch == '\\' && inString -> escaped = true
                    ch == '"' -> inString = !inString
                    !inString -> {
                        if (ch == '{' || ch == '[') {
                            stack.push(index to ch)
                        } else if (ch == '}' || ch == ']') {
                            if (stack.isNotEmpty()) {
                                val (topIndex, topChar) = stack.peek()
                                if ((topChar == '{' && ch == '}') || (topChar == '[' && ch == ']')) {
                                    stack.pop()
                                    if (index > topIndex) {
                                        foldRanges[topIndex] = index
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        var stringStart: Int? = null
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (stringStart == null) {
                if (trimmed.startsWith("\"") && trimmed.contains(":")) {
                    val valPart = trimmed.substringAfter(":").trim()
                    if (valPart.startsWith("\"") && countQuotes(valPart) % 2 != 0) {
                        stringStart = index
                    }
                }
            } else {
                if (countQuotes(trimmed) % 2 != 0) {
                    foldRanges[stringStart] = index
                    stringStart = null
                }
            }
        }

        return foldRanges
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex < 0 || endLineIndex >= lines.size) return ""
        val endLineTrimmed = lines[endLineIndex].trim()
        return when {
            endLineTrimmed.startsWith("}") && endLineTrimmed.endsWith(",") -> "}, "
            endLineTrimmed.startsWith("}") -> "} "
            endLineTrimmed.startsWith("]") && endLineTrimmed.endsWith(",") -> "], "
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
        val keyColor = Color(0xFF79C0FF)
        val stringColor = Color(0xFFA5D6FF)
        val numberColor = Color(0xFFD2A8FF)
        val booleanColor = Color(0xFFFFAB70)

        val parsedKv = remember(lineText) { parseJsonKeyValueLine(lineText) }
        val isKeyValue = parsedKv != null
        val keyPart = parsedKv?.keyPart ?: ""
        val valPart = parsedKv?.valPart ?: ""
        val trimmedVal = valPart.trim()

        val isLongString = isKeyValue && trimmedVal.startsWith("\"") && trimmedVal.length > 60
        var isStringExpanded by remember(lineText) { mutableStateOf(false) }

        if (isKeyValue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = keyPart,
                    color = keyColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Text(
                    text = ":",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                val valColor = when {
                    trimmedVal.startsWith("\"") -> stringColor
                    trimmedVal.startsWith("true") || trimmedVal.startsWith("false") -> booleanColor
                    trimmedVal.firstOrNull()?.isDigit() == true -> numberColor
                    else -> Color.White
                }

                if (isLongString && !isStringExpanded) {
                    val prefixSpaces = valPart.takeWhile { it.isWhitespace() }
                    val truncatedVal = prefixSpaces + trimmedVal.take(45) + "..."
                    Text(
                        text = truncatedVal,
                        color = valColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                            .border(1.dp, KNetColors.ActiveBlue.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .clickable { isStringExpanded = true }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        val sizeKb = "%.1f KB".format(trimmedVal.length / 1024.0)
                        Text(
                            text = "[... $sizeKb]",
                            color = KNetColors.ActiveBlue,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = valPart,
                            color = valColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            softWrap = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLongString && isStringExpanded) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                    .clickable { isStringExpanded = false }
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "[collapse]",
                                    color = KNetColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (isCollapsed) {
                    CollapsedBadge(closingSymbol = closingSymbol, onToggleFold = onToggleFold)
                }
            }
        } else {
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
                if (isCollapsed) {
                    CollapsedBadge(closingSymbol = closingSymbol, onToggleFold = onToggleFold)
                }
            }
        }
    }

    private fun countQuotes(str: String): Int {
        var count = 0
        var escaped = false
        for (ch in str) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                count++
            }
        }
        return count
    }
}
