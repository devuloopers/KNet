package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLineVisibilityEngine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LineFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.CodeHighlighterRegistry
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.TokenMaker
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.TokenState
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Virtualized zero-blocking code viewer composable powered by [LazyColumn].
 *
 * Renders only visible lines in the viewport and supports fold regions natively,
 * enabling instant non-blocking switching and smooth 60 FPS scrolling for documents
 * ranging from 5 lines up to 100,000+ lines.
 *
 * @param rawLines Complete list of document lines split off the UI thread.
 * @param foldRegions Calculated fold regions for this document.
 * @param collapsedFoldStartLines Set of 0-indexed start line indices that are currently collapsed.
 * @param onToggleFold Callback fired when a fold arrow is clicked.
 * @param isFoldingEnabled True if fold arrows are visible and interactive.
 * @param languageHint Optional programming language identifier (e.g. "json", "html", "xml").
 * @param fontSize Monospace font size for line text and numbers.
 * @param lineHeight Line height spacing for document rows.
 * @param modifier Composable layout modifier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyReadOnlyBody(
    rawLines: List<String>,
    foldRegions: List<FoldRegion> = emptyList(),
    collapsedFoldStartLines: Set<Int> = emptySet(),
    onToggleFold: (originalLineIndex: Int) -> Unit = {},
    isFoldingEnabled: Boolean = true,
    isWordWrapEnabled: Boolean = true,
    languageHint: String? = null,
    fontSize: TextUnit = CodeEditorTokens.FontSize,
    lineHeight: TextUnit = CodeEditorTokens.LineHeight,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    val visibleLines: List<LazyLine> = remember(rawLines, foldRegions, collapsedFoldStartLines, isFoldingEnabled) {
        if (isFoldingEnabled && foldRegions.isNotEmpty()) {
            LazyLineVisibilityEngine.buildVisibleLines(rawLines, foldRegions, collapsedFoldStartLines)
        } else {
            rawLines.mapIndexed { i, text -> LazyLine(i, text, LineFoldState.None) }
        }
    }

    val maxDigits = remember(rawLines.size) {
        rawLines.size.toString().length.coerceAtLeast(3)
    }
    val gutterWidthDp = remember(maxDigits) {
        (maxDigits * 8 + 12).dp
    }

    val highlighter = remember(languageHint) {
        if (languageHint != null) {
            CodeHighlighterRegistry.resolveByLanguage(languageHint)
        } else {
            null
        }
    }

    val scrollbarStyle = remember {
        ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 8.dp,
            shape = RoundedCornerShape(4.dp),
            hoverDurationMillis = 150,
            unhoverColor = EditorColors.BorderDark.copy(alpha = 0.5f),
            hoverColor = EditorColors.ActiveBlue
        )
    }

    val copyAction = rememberClipboardCopyAction()

    val customTextContextMenu = remember(copyAction) {
        object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit
            ) {
                val selectedText = textManager.selectedText.text
                val hasSelection = selectedText.isNotBlank()
                val menuItems = mutableListOf<ContextMenuItem>()

                if (hasSelection) {
                    menuItems.add(
                        ContextMenuItem(
                            label = "Copy Selected Text",
                            shortcut = "Ctrl+C",
                            onClick = {
                                copyAction(selectedText)
                            }
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

    CompositionLocalProvider(LocalTextContextMenu provides customTextContextMenu) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(EditorColors.BackgroundDark)
                .padding(CodeEditorTokens.ContainerPadding)
        ) {
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = visibleLines,
                        key = { _, line -> line.originalLineIndex }
                    ) { _, line ->
                        val highlightedText = remember(line.displayText, highlighter) {
                            highlighter?.highlightLine(line.displayText)
                                ?: TokenMaker.tokenizeLine(line.displayText, TokenState.NULL).annotatedString
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (!isWordWrapEnabled) Modifier.height(CodeEditorTokens.GutterLineHeightDp) else Modifier),
                            verticalAlignment = if (isWordWrapEnabled) Alignment.Top else Alignment.CenterVertically
                        ) {
                            // Fold Arrow Indicator & Line Number Gutter (Disabled Selection)
                            DisableSelection {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isFoldingEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(CodeEditorTokens.GutterLineHeightDp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (line.foldState) {
                                                LineFoldState.FoldStartExpanded -> {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "Collapse block",
                                                        tint = EditorColors.TextSecondary,
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .clickable { onToggleFold(line.originalLineIndex) }
                                                    )
                                                }

                                                LineFoldState.FoldStartCollapsed -> {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = "Expand block",
                                                        tint = EditorColors.ActiveBlue,
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .clickable { onToggleFold(line.originalLineIndex) }
                                                    )
                                                }

                                                LineFoldState.None -> {
                                                    // Empty gutter spacer for lines without fold arrows
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    Text(
                                        text = (line.originalLineIndex + 1).toString(),
                                        color = Color(0xFF484F58),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSize,
                                        style = CodeEditorTokens.editorTextStyle(
                                            fontSize = fontSize,
                                            lineHeight = lineHeight
                                        ),
                                        modifier = Modifier.width(gutterWidthDp),
                                        textAlign = TextAlign.End
                                    )

                                    Spacer(modifier = Modifier.width(CodeEditorTokens.GutterPaddingEnd))
                                }
                            }

                            // Code Line Text
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (!isWordWrapEnabled) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                            ) {
                                Text(
                                    text = highlightedText,
                                    style = CodeEditorTokens.editorTextStyle(
                                        fontSize = fontSize,
                                        lineHeight = lineHeight
                                    ).copy(
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    softWrap = isWordWrapEnabled,
                                    maxLines = if (isWordWrapEnabled) Int.MAX_VALUE else 1
                                )
                            }
                        }
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                style = scrollbarStyle
            )
        }
    }
}
