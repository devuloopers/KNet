package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LineFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/**
 * Virtualized gutter slot composable for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 *
 * Renders the fold arrow indicator and line number for a single document line inside a
 * [androidx.compose.foundation.lazy.LazyColumn] item row. Uses clean, neutral VS Code-style gutter aesthetics
 * without boxy background overlays or distracting color shifts.
 *
 * @param displayLineNumber 1-indexed line number to render in the gutter.
 * @param foldState Current fold state of this line (none, expanded, or collapsed start).
 * @param isFoldingEnabled True if fold arrows are rendered and interactive.
 * @param gutterWidthDp Calculated pixel width allocated to the line number text column.
 * @param fontSize Monospace font size for the line number text.
 * @param lineHeight Line height spacing for the gutter row.
 * @param strings Localizable accessibility labels.
 * @param onToggleFold Callback fired when the fold arrow icon is clicked.
 */
@Composable
internal fun LazyCodeGutterSlot(
    displayLineNumber: Int,
    foldState: LineFoldState,
    isFoldingEnabled: Boolean,
    gutterWidthDp: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    strings: CodeEditorStrings,
    onToggleFold: () -> Unit
) {
    DisableSelection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isFoldingEnabled) {
                val hasFold = foldState != LineFoldState.None
                val isExpanded = foldState == LineFoldState.FoldStartExpanded
                val isCollapsed = foldState == LineFoldState.FoldStartCollapsed

                val targetRotation = if (isExpanded) 90f else 0f
                val targetColor = if (isCollapsed) EditorColors.ActiveBlue else EditorColors.TextSecondary

                val rotation by animateFloatAsState(
                    targetValue = targetRotation,
                    animationSpec = tween(durationMillis = 120)
                )

                val tintColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(durationMillis = 120)
                )

                val interactionSource = remember { MutableInteractionSource() }

                // Fold arrow indicator box — fixed 16 dp width regardless of fold state.
                // Full box is clickable to enlarge touch target without visual ripple flash.
                Box(
                    modifier = Modifier
                        .width(CodeEditorTokens.FoldArrowHitTargetWidth)
                        .height(CodeEditorTokens.GutterLineHeightDp)
                        .then(
                            if (hasFold) {
                                Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = onToggleFold
                                    )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasFold) {
                        Icon(
                            imageVector = KNetIcons.ChevronRight,
                            contentDescription = if (isExpanded) strings.collapseBlock else strings.expandBlock,
                            tint = tintColor,
                            modifier = Modifier
                                .size(CodeEditorTokens.FoldArrowIconSize)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(CodeEditorTokens.FoldArrowPaddingEnd))
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
