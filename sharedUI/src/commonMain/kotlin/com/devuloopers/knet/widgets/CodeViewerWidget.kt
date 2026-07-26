package com.devuloopers.knet.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.highlighter.CodeHighlighterRegistry
import com.devuloopers.knet.highlighter.CodeLanguageHighlighter
import com.devuloopers.knet.theme.KNetColors

/**
 * Reusable Code Viewer Component with line numbering, JetBrains-style code folding,
 * search filtering, modular strategy-based syntax highlighting, and custom dark context menu text selection/copying support.
 *
 * @param codeText Formatted multi-line text to display.
 * @param bodyFormat Resolved body payload format (JSON, HTML, XML, etc.) for strategy selection.
 * @param languageHint Optional explicit language string hint ("json", "html", "xml", "plain").
 * @param searchQuery Search query filter string.
 * @param modifier Resizing constraints.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeViewerWidget(
    codeText: String,
    bodyFormat: BodyFormat? = null,
    languageHint: String? = null,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val highlighter: CodeLanguageHighlighter = remember(bodyFormat, languageHint) {
        if (!languageHint.isNullOrBlank()) {
            CodeHighlighterRegistry.resolveByLanguage(languageHint)
        } else {
            CodeHighlighterRegistry.resolve(bodyFormat)
        }
    }

    val lines = remember(codeText) { codeText.split("\n") }
    val foldRanges = remember(lines, highlighter) { highlighter.calculateFoldRanges(lines) }
    var collapsedStartLines by remember(codeText) { mutableStateOf(setOf<Int>()) }

    val isSearching = searchQuery.isNotBlank()
    val copyAction = rememberClipboardCopyAction()

    val customTextContextMenu = remember(codeText, foldRanges, collapsedStartLines) {
        object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit
            ) {
                val hasSelection = textManager.selectedText.text.isNotEmpty()
                val menuItems = mutableListOf<ContextMenuItem>()

                if (hasSelection) {
                    menuItems.add(
                        ContextMenuItem(
                            label = "Copy Selected Text",
                            shortcut = "Ctrl+C",
                            onClick = { copyAction(textManager.selectedText.text) }
                        )
                    )
                }

                menuItems.add(
                    ContextMenuItem(
                        label = "Copy Formatted Body",
                        shortcut = if (!hasSelection) "Ctrl+C" else null,
                        onClick = { copyAction(codeText) }
                    )
                )

                if (foldRanges.isNotEmpty()) {
                    menuItems.add(
                        ContextMenuItem(
                            label = "Expand All Blocks",
                            onClick = { collapsedStartLines = emptySet() }
                        )
                    )
                    menuItems.add(
                        ContextMenuItem(
                            label = "Collapse All Blocks",
                            onClick = { collapsedStartLines = foldRanges.keys.toSet() }
                        )
                    )
                }

                KNetContextMenuArea(
                    items = menuItems,
                    modifier = Modifier.fillMaxSize(),
                    content = content
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top-Left: Total line count indicator
            val lineCountLabel = if (lines.size == 1) "1 line" else "${lines.size} lines"
            Text(
                text = lineCountLabel,
                color = KNetColors.TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            // Top-Right: Expand All | Collapse All controls
            if (foldRanges.isNotEmpty() && !isSearching) {
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

        CompositionLocalProvider(LocalTextContextMenu provides customTextContextMenu) {
            SelectionContainer(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    closingSymbol = "",
                                    highlighter = highlighter,
                                    onToggleFold = {}
                                )
                            }
                            continue
                        }

                        if (index <= skipUntilIndex) continue

                        val isFoldable = foldRanges.containsKey(index)
                        val isCollapsed = collapsedStartLines.contains(index)
                        val endLineIndex = foldRanges[index]
                        val closingSymbol = if (endLineIndex != null) {
                            highlighter.resolveClosingSymbol(lines, endLineIndex)
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
                            highlighter = highlighter,
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
    }
}

/**
 * Individual Code Line Renderer with fold arrow gutter and language strategy delegation.
 */
@Composable
fun CodeLineView(
    lineNumber: Int,
    lineText: String,
    isFoldable: Boolean,
    isCollapsed: Boolean,
    closingSymbol: String,
    highlighter: CodeLanguageHighlighter,
    onToggleFold: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        DisableSelection {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                        .clickable(enabled = isFoldable) { onToggleFold() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isFoldable) {
                        Icon(
                            imageVector = if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isCollapsed) "Expand" else "Collapse",
                            tint = if (isCollapsed) KNetColors.ActiveBlue else KNetColors.TextSecondary,
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
            }
        }

        highlighter.RenderLineContent(
            lineNumber = lineNumber,
            lineText = lineText,
            isFoldable = isFoldable,
            isCollapsed = isCollapsed,
            closingSymbol = closingSymbol,
            onToggleFold = onToggleFold
        )
    }
}
