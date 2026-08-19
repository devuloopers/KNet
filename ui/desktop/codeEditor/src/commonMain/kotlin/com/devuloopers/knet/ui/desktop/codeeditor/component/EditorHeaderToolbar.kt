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
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Configurable header toolbar displaying line count and editor actions.
 *
 * Employs horizontal scroll and single-line text stability to ensure fold action controls
 * and indicators never get squished, wrapped, or unresponsive during window/panel resizing.
 *
 * @param totalLines Total line count in the full document.
 * @param showLineCountHeader True if line count indicator is displayed.
 * @param showFoldActionsHeader True if [Expand All | Collapse All] controls are displayed.
 * @param hasFoldRegions True if document has fold regions.
 * @param strings Localizable action labels.
 * @param onCopyAll Callback to asynchronously copy full un-truncated text to system clipboard.
 * @param onExpandAll Callback to expand all folded blocks.
 * @param onCollapseAll Callback to collapse all top-level blocks.
 */
@Composable
internal fun EditorHeaderToolbar(
    totalLines: Int,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    hasFoldRegions: Boolean,
    strings: CodeEditorStrings,
    onCopyAll: (() -> Unit)? = null,
    onPrettify: (() -> Unit)? = null,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit
) {
    if (!showLineCountHeader && (!showFoldActionsHeader || !hasFoldRegions) && onPrettify == null) return

    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(bottom = CodeEditorTokens.HeaderBottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLineCountHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeEditorTokens.HeaderActionSpacing)
            ) {
                val noun = if (totalLines == 1) strings.singularLine else strings.pluralLines
                Text(
                    text = "$totalLines $noun",
                    color = EditorColors.TextSecondary,
                    fontSize = CodeEditorTokens.HeaderFontSize,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        } else {
            Spacer(modifier = Modifier.width(CodeEditorTokens.BorderWidth))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeEditorTokens.HeaderActionSpacing)
        ) {
            if (onPrettify != null) {
                Row(
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius)
                        )
                        .border(
                            CodeEditorTokens.BorderWidth,
                            EditorColors.BorderDark,
                            RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius)
                        )
                        .clip(RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius))
                        .clickable(onClick = onPrettify)
                        .handCursor()
                        .padding(
                            horizontal = CodeEditorTokens.HeaderActionHorizontalPadding,
                            vertical = CodeEditorTokens.HeaderActionVerticalPadding
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.prettify,
                        color = EditorColors.ActiveBlue,
                        fontSize = CodeEditorTokens.HeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            if (showFoldActionsHeader && hasFoldRegions) {
                Row(
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius)
                        )
                        .border(
                            CodeEditorTokens.BorderWidth,
                            EditorColors.BorderDark,
                            RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius)
                        )
                        .padding(
                            horizontal = CodeEditorTokens.FoldActionHorizontalPadding,
                            vertical = CodeEditorTokens.HeaderActionVerticalPadding
                        ),
                    horizontalArrangement = Arrangement.spacedBy(CodeEditorTokens.HeaderActionSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.expandAll,
                        color = EditorColors.ActiveBlue,
                        fontSize = CodeEditorTokens.HeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .clip(RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius))
                            .clickable(onClick = onExpandAll)
                            .handCursor()
                    )
                    Text(
                        text = "|",
                        color = EditorColors.TextSecondary.copy(alpha = 0.4f),
                        fontSize = CodeEditorTokens.HeaderFontSize,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = strings.collapseAll,
                        color = EditorColors.ActiveBlue,
                        fontSize = CodeEditorTokens.HeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .clip(RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius))
                            .clickable(onClick = onCollapseAll)
                            .handCursor()
                    )
                }
            }
        }
    }
}
