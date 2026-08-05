package com.devuloopers.knet.ui.desktop.codeeditor.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single Source of Truth for all Code Editor & Code Viewer layout and typography constants.
 *
 * Pattern: JetBrains IntelliJ EditorColorsScheme / VS Code editorDefaults.
 */
object CodeEditorTokens {

    // ─── Typography ──────────────────────────────────────────────────────────────

    /**
     * Monospace font size for all editor text: code, line numbers, and placeholder hints.
     * All three must share the same value to ensure vertical rhythm consistency.
     */
    val FontSize = 12.sp

    /**
     * Vertical line height for all editor text rows.
     * Must match [GutterLineHeightDp] (16.sp ≈ 16.dp at 1x density).
     * Controls the height of BasicTextField lines and gutter rows.
     */
    val LineHeight = 16.sp

    // ─── Gutter Layout ───────────────────────────────────────────────────────────

    /**
     * Fixed pixel height of each gutter Row.
     * Must match [LineHeight] exactly to guarantee 1:1 line number ↔ code line alignment.
     */
    val GutterLineHeightDp = 16.dp

    /**
     * Minimum width of the line number text area. Auto-expands for 4-digit line numbers (1000+).
     */
    val GutterNumberMinWidth = 28.dp

    /**
     * Fixed size of the fold arrow clickable Box in the gutter.
     */
    val FoldArrowBoxSize = 16.dp

    /**
     * Horizontal gap between fold arrow and line number text.
     */
    val FoldArrowPaddingEnd = 4.dp

    /**
     * Horizontal gap between end of gutter block and start of code text.
     */
    val GutterPaddingEnd = 8.dp

    // ─── Editor Container ─────────────────────────────────────────────────────────

    /**
     * Internal padding inside the editor/viewer container box.
     */
    val ContainerPadding = 8.dp

    /**
     * Top and bottom boundary threshold (hot zone) for activating text selection drag auto-scrolling.
     */
    val AutoScrollActivationZone = 40.dp

    /**
     * Vertical gap between lines in LazyColumn.
     */
    val ViewerLineSpacing = 0.dp

    // ─── Shared TextStyle Factory ──────────────────────────────────────────────────

    /**
     * Returns the canonical [TextStyle] used for all editor text renderings.
     */
    fun editorTextStyle(
        fontSize: TextUnit = FontSize,
        lineHeight: TextUnit = LineHeight
    ): TextStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
}
