package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.CollapsedFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentLayoutMap
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.getOriginalLineNumber
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * 2-Column Structural Gutter Slot rendering absolute Y-offset line numbers and fold controls.
 *
 * @param lineCount Number of displayed logical lines.
 * @param activeLineIndex 0-indexed line containing active caret (or -1 if read-only).
 * @param lineTopOffsetsDp Calculated vertical Y-offsets for each line.
 * @param collapsedFolds Active map of collapsed fold states.
 * @param foldStartLines Map of fold start line to [FoldRegion].
 * @param isFoldingEnabled True if fold arrows are visible and interactive.
 * @param isIconArrowStyle True if material arrow icons are used (read-only view), false if text arrows are used (editable mode).
 * @param layoutMap Single Source of Truth [DocumentLayoutMap] instance.
 * @param onToggleFold Callback executed when fold arrow is clicked.
 */
@Composable
fun EditorGutter(
    lineCount: Int,
    activeLineIndex: Int,
    lineTopOffsetsDp: List<Dp>,
    collapsedFolds: Map<Int, CollapsedFoldState>,
    foldStartLines: Map<Int, FoldRegion>,
    isFoldingEnabled: Boolean,
    isIconArrowStyle: Boolean = false,
    layoutMap: DocumentLayoutMap? = null,
    onToggleFold: (Int) -> Unit
) {
    val gutterWidth = if (isFoldingEnabled) {
        16.dp + 4.dp + CodeEditorTokens.GutterNumberMinWidth
    } else {
        CodeEditorTokens.GutterNumberMinWidth
    }

    Box(
        modifier = Modifier
            .padding(top = 0.dp, end = CodeEditorTokens.GutterPaddingEnd)
            .width(gutterWidth)
    ) {
        (0 until lineCount).forEach { index ->
            val isActiveLine = index == activeLineIndex
            val isCurrentlyCollapsed = collapsedFolds.containsKey(index)
            val canCollapse = foldStartLines.containsKey(index)
            val showFoldArrow = isFoldingEnabled && (isCurrentlyCollapsed || canCollapse)
            val topOffset = lineTopOffsetsDp.getOrNull(index) ?: (index.toFloat() * CodeEditorTokens.GutterLineHeightDp.value).dp

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .offset(y = topOffset)
                    .fillMaxWidth()
                    .height(CodeEditorTokens.GutterLineHeightDp)
            ) {
                if (isFoldingEnabled) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(CodeEditorTokens.GutterLineHeightDp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (showFoldArrow) {
                            if (isIconArrowStyle) {
                                Icon(
                                    imageVector = if (isCurrentlyCollapsed) {
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                    contentDescription = if (isCurrentlyCollapsed) "Expand" else "Collapse",
                                    tint = if (isCurrentlyCollapsed) EditorColors.ActiveBlue else EditorColors.TextSecondary,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onToggleFold(index) }
                                )
                            } else {
                                Text(
                                    text = if (isCurrentlyCollapsed) "►" else "▼",
                                    color = if (isCurrentlyCollapsed) EditorColors.ActiveBlue else Color(0xFF6E7681),
                                    style = CodeEditorTokens.editorTextStyle().copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentlyCollapsed) EditorColors.ActiveBlue else Color(0xFF6E7681)
                                    ),
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onToggleFold(index) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(
                    modifier = Modifier
                        .width(CodeEditorTokens.GutterNumberMinWidth)
                        .height(CodeEditorTokens.GutterLineHeightDp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    val originalLineNum = if (layoutMap != null) {
                        getOriginalLineNumber(index, layoutMap)
                    } else {
                        getOriginalLineNumber(index, collapsedFolds)
                    }
                    Text(
                        text = originalLineNum.toString(),
                        color = if (isActiveLine) EditorColors.ActiveBlue else Color(0xFF484F58),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                        style = CodeEditorTokens.editorTextStyle(),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
