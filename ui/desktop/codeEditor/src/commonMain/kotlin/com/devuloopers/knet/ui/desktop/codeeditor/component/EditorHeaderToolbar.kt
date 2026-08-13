package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Configurable Header Toolbar displaying total line count indicators, truncation badges, and Copy/Fold action controls.
 *
 * Employs horizontal scroll and single-line text stability to ensure fold action controls
 * and indicators never get squished, wrapped, or unresponsive during window/panel resizing.
 *
 * @param totalLines Total line count in the full document.
 * @param showLineCountHeader True if line count indicator is displayed.
 * @param showFoldActionsHeader True if [Expand All | Collapse All] controls are displayed.
 * @param hasFoldRegions True if document has fold regions.
 * @param isHighPerformanceMode True if high-performance fast rendering mode is active for large files.
 * @param isTruncated True if line preview windowing is active (e.g. 100,000 lines truncated to 10,000).
 * @param displayedLines Number of lines currently rendered in the editor viewport.
 * @param onCopyAll Callback to asynchronously copy full un-truncated text to system clipboard.
 * @param onExpandAll Callback to expand all folded blocks.
 * @param onCollapseAll Callback to collapse all top-level blocks.
 */
@Composable
fun EditorHeaderToolbar(
    totalLines: Int,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    hasFoldRegions: Boolean,
    isHighPerformanceMode: Boolean = false,
    isTruncated: Boolean = false,
    displayedLines: Int = totalLines,
    onCopyAll: (() -> Unit)? = null,
    onPrettify: (() -> Unit)? = null,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit
) {
    if (!showLineCountHeader && (!showFoldActionsHeader || !hasFoldRegions) && !isHighPerformanceMode && !isTruncated && onPrettify == null) return

    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLineCountHeader || isHighPerformanceMode || isTruncated) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showLineCountHeader && !isTruncated) {
                    val lineCountLabel = if (totalLines == 1) "1 line" else "$totalLines lines"
                    Text(
                        text = lineCountLabel,
                        color = EditorColors.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }

                if (isHighPerformanceMode) {
                    Row(
                        modifier = Modifier
                            .background(Color(0x1A3FB950), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0x4D3FB950), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "High-Performance Fast Rendering",
                            color = Color(0xFF3FB950),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onPrettify != null) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        .border(1.dp, EditorColors.BorderDark, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onPrettify)
                        .handCursor()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Prettify",
                        color = EditorColors.ActiveBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            if (showFoldActionsHeader && hasFoldRegions && !isHighPerformanceMode && !isTruncated) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        .border(1.dp, EditorColors.BorderDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Expand All",
                        color = EditorColors.ActiveBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onExpandAll)
                            .handCursor()
                    )
                    Text(
                        text = "|",
                        color = EditorColors.TextSecondary.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Collapse All",
                        color = EditorColors.ActiveBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onCollapseAll)
                            .handCursor()
                    )
                }
            }
        }
    }
}
