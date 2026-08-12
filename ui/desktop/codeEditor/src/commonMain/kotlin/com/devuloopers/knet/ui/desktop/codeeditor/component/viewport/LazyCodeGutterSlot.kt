package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LineFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Virtualized gutter slot composable for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 *
 * Renders the fold arrow indicator and line number for a single document line inside a
 * [androidx.compose.foundation.lazy.LazyColumn] item row. Selection is disabled via
 * [DisableSelection] to prevent gutter numbers from being included in drag-select copies.
 *
 * @param displayLineNumber 1-indexed line number to render in the gutter.
 * @param foldState Current fold state of this line (none, expanded, or collapsed start).
 * @param isFoldingEnabled True if fold arrows are rendered and interactive.
 * @param gutterWidthDp Calculated pixel width allocated to the line number text column.
 * @param fontSize Monospace font size for the line number text.
 * @param lineHeight Line height spacing for the gutter row.
 * @param onToggleFold Callback fired when the fold arrow icon is clicked.
 */
@Composable
fun LazyCodeGutterSlot(
    displayLineNumber: Int,
    foldState: LineFoldState,
    isFoldingEnabled: Boolean,
    gutterWidthDp: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onToggleFold: () -> Unit
) {
    DisableSelection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isFoldingEnabled) {
                // Fold arrow indicator box — fixed 16 dp width regardless of fold state.
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(CodeEditorTokens.GutterLineHeightDp),
                    contentAlignment = Alignment.Center
                ) {
                    when (foldState) {
                        LineFoldState.FoldStartExpanded -> {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse block",
                                tint = EditorColors.TextSecondary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable(onClick = onToggleFold)
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
                                    .clickable(onClick = onToggleFold)
                            )
                        }

                        LineFoldState.None -> {
                            // No fold arrow — empty space to preserve gutter alignment.
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
            }

            // Line number text — right-aligned inside the calculated gutter width.
            Text(
                text = displayLineNumber.toString(),
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
}
