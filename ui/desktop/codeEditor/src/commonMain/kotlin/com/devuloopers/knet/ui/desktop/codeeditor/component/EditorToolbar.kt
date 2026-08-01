package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

@Composable
internal fun EditorToolbar(
    totalLines: Int,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    hasFoldRegions: Boolean,
    isHighPerformanceMode: Boolean,
    isTruncated: Boolean = false,
    displayedLines: Int = totalLines,
    onCopyAll: (() -> Unit)? = null,
    onExpandAll: (() -> Unit)? = null,
    onCollapseAll: (() -> Unit)? = null
) {
    if (!showLineCountHeader && !showFoldActionsHeader) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLineCountHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isTruncated) "$displayedLines of $totalLines lines (preview mode)" else "$totalLines lines",
                    color = EditorColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                if (isHighPerformanceMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• Fast Render Mode",
                        color = Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onCopyAll != null) {
                Text(
                    text = "Copy All",
                    color = EditorColors.ActiveBlue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onCopyAll() }
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (showFoldActionsHeader && hasFoldRegions && !isHighPerformanceMode) {
                if (onExpandAll != null) {
                    Text(
                        text = "Expand All",
                        color = EditorColors.ActiveBlue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onExpandAll() }
                    )
                }
                if (onCollapseAll != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Collapse All",
                        color = EditorColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onCollapseAll() }
                    )
                }
            }
        }
    }
}
