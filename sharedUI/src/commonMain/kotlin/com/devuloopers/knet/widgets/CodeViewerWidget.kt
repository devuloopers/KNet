package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors
import java.util.ArrayDeque

private fun countUnescapedQuotes(str: String): Int {
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

/**
 * Calculates bracket matching ranges and multi-line string ranges for code folding.
 *
 * @param lines Formatted code text split by newlines.
 * @return Map of `startLineIndex -> endLineIndex` matching opening and closing brackets/strings.
 */
fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
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

    // Detect multi-line unclosed string properties (e.g. Base64 blobs spanning lines)
    var stringStart: Int? = null
    for ((index, line) in lines.withIndex()) {
        val trimmed = line.trim()
        if (stringStart == null) {
            if (trimmed.startsWith("\"") && trimmed.contains(":")) {
                val valPart = trimmed.substringAfter(":").trim()
                if (valPart.startsWith("\"") && countUnescapedQuotes(valPart) % 2 != 0) {
                    stringStart = index
                }
            }
        } else {
            if (countUnescapedQuotes(trimmed) % 2 != 0) {
                foldRanges[stringStart] = index
                stringStart = null
            }
        }
    }

    return foldRanges
}

/**
 * Reusable Code Viewer Component with line numbering, JetBrains-style code folding (expand/collapse blocks),
 * search filtering, and syntax highlighting.
 *
 * @param codeText Formatted multi-line text to display.
 * @param searchQuery Search query filter string.
 * @param modifier Resizing constraints.
 */
@Composable
fun CodeViewerWidget(
    codeText: String,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val lines = remember(codeText) { codeText.split("\n") }
    val foldRanges = remember(lines) { calculateFoldRanges(lines) }
    var collapsedStartLines by remember(codeText) { mutableStateOf(setOf<Int>()) }

    val isSearching = searchQuery.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (foldRanges.isNotEmpty() && !isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Expand All",
                        color = KNetColors.ActiveBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { collapsedStartLines = emptySet() }
                    )
                    Text(text = "|", color = KNetColors.TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "Collapse All",
                        color = KNetColors.ActiveBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { collapsedStartLines = foldRanges.keys.toSet() }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            var skipUntilIndex = -1

            for (index in lines.indices) {
                if (isSearching) {
                    val line = lines[index]
                    if (line.contains(searchQuery, ignoreCase = true)) {
                        CodeLineView(
                            lineNumber = index + 1,
                            lineText = line,
                            isFoldable = false,
                            isCollapsed = false,
                            onToggleFold = {}
                        )
                    }
                    continue
                }

                if (index <= skipUntilIndex) continue

                val isFoldable = foldRanges.containsKey(index)
                val isCollapsed = collapsedStartLines.contains(index)

                val endLineIndex = foldRanges[index]
                val closingSymbol = if (endLineIndex != null && endLineIndex < lines.size) {
                    val endLineTrimmed = lines[endLineIndex].trim()
                    when {
                        endLineTrimmed.startsWith("}") && endLineTrimmed.endsWith(",") -> "}, "
                        endLineTrimmed.startsWith("}") -> "} "
                        endLineTrimmed.startsWith("]") && endLineTrimmed.endsWith(",") -> "], "
                        endLineTrimmed.startsWith("]") -> "] "
                        else -> ""
                    }
                } else ""

                if (isCollapsed && endLineIndex != null) {
                    skipUntilIndex = endLineIndex
                }

                CodeLineView(
                    lineNumber = index + 1,
                    lineText = lines[index],
                    isFoldable = isFoldable,
                    isCollapsed = isCollapsed,
                    closingSymbol = closingSymbol,
                    onToggleFold = {
                        collapsedStartLines = if (isCollapsed) {
                            collapsedStartLines - index
                        } else {
                            collapsedStartLines + index
                        }
                    }
                )
            }
        }
    }
}

data class ParsedJsonKeyValue(
    val leadingIndent: String,
    val keyPart: String,
    val valPart: String
)

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

/**
 * Individual Code Line Renderer with fold arrow gutter and syntax highlighting.
 *
 * @param lineNumber 1-based line number.
 * @param lineText Line text contents.
 * @param isFoldable True if this line starts a foldable block range.
 * @param isCollapsed True if this line's block range is currently collapsed.
 * @param closingSymbol Matching closing bracket symbol with trailing comma.
 * @param onToggleFold Callback invoked when fold arrow or collapsed badge is clicked.
 */
@Composable
fun CodeLineView(
    lineNumber: Int,
    lineText: String,
    isFoldable: Boolean,
    isCollapsed: Boolean,
    closingSymbol: String = "",
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

    val effectiveFoldable = isFoldable || isLongString
    val effectiveCollapsed = isCollapsed || (isLongString && !isStringExpanded && !isFoldable)

    val handleFoldToggle = {
        if (isLongString && !isFoldable) {
            isStringExpanded = !isStringExpanded
        } else {
            onToggleFold()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .clickable(enabled = effectiveFoldable) { handleFoldToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (effectiveFoldable) {
                Icon(
                    imageVector = if (effectiveCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (effectiveCollapsed) "Expand" else "Collapse",
                    tint = if (effectiveCollapsed) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = lineNumber.toString(),
            color = Color(0xFF484F58),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(top = 2.dp)
                .width(28.dp),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.width(12.dp))

        if (isKeyValue) {
            Row(
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1f),
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
}

@Composable
private fun CollapsedBadge(closingSymbol: String, onToggleFold: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                .border(1.dp, KNetColors.ActiveBlue.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                .clickable { onToggleFold() }
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "...",
                color = KNetColors.ActiveBlue,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        if (closingSymbol.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = closingSymbol.trim(),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

