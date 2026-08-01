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
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.getOriginalLineNumber
import com.devuloopers.knet.ui.desktop.codeeditor.model.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorTokens

@Composable
internal fun EditorGutter(
    lineCount: Int,
    activeLineIndex: Int,
    lineTopOffsetsDp: List<Dp>,
    collapsedFolds: Map<Int, CollapsedFoldState>,
    foldStartLines: Map<Int, FoldRegion>,
    isFoldingEnabled: Boolean,
    isIconArrowStyle: Boolean,
    onToggleFold: (Int) -> Unit
) {
    val dynamicMinWidth = when {
        lineCount >= 10000 -> 36.dp
        lineCount >= 1000 -> 30.dp
        else -> EditorTokens.GutterNumberMinWidth
    }

    Box(
        modifier = Modifier
            .padding(end = EditorTokens.GutterPaddingEnd)
    ) {
        if (lineTopOffsetsDp.size >= lineCount) {
            for (i in 0 until lineCount) {
                val topDp = lineTopOffsetsDp[i]
                val isActive = i == activeLineIndex
                val displayLineNumber = getOriginalLineNumber(i, collapsedFolds)
                val isFoldStart = isFoldingEnabled && foldStartLines.containsKey(i)
                val isCollapsed = collapsedFolds.containsKey(i)

                Row(
                    modifier = Modifier
                        .offset(y = topDp)
                        .height(EditorTokens.GutterLineHeightDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(EditorTokens.FoldArrowBoxSize),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFoldStart) {
                            if (isIconArrowStyle) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isCollapsed) "Expand block" else "Collapse block",
                                    tint = EditorColors.FoldIconTint,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onToggleFold(i) }
                                )
                            } else {
                                Text(
                                    text = if (isCollapsed) "{+}" else "{-}",
                                    color = EditorColors.FoldIconTint,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onToggleFold(i) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(EditorTokens.FoldArrowPaddingEnd))

                    Text(
                        text = displayLineNumber.toString(),
                        color = if (isActive) EditorColors.ActiveLineNumber else EditorColors.InactiveLineNumber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = EditorTokens.FontSize,
                        lineHeight = EditorTokens.LineHeight,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(dynamicMinWidth)
                    )
                }
            }
        } else {
            for (i in 0 until lineCount) {
                val isActive = i == activeLineIndex
                val displayLineNumber = getOriginalLineNumber(i, collapsedFolds)

                Row(
                    modifier = Modifier.height(EditorTokens.GutterLineHeightDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(EditorTokens.FoldArrowBoxSize + EditorTokens.FoldArrowPaddingEnd))

                    Text(
                        text = displayLineNumber.toString(),
                        color = if (isActive) EditorColors.ActiveLineNumber else EditorColors.InactiveLineNumber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = EditorTokens.FontSize,
                        lineHeight = EditorTokens.LineHeight,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(dynamicMinWidth)
                    )
                }
            }
        }
    }
}
