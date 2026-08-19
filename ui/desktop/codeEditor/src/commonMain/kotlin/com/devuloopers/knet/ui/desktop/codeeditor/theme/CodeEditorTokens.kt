package com.devuloopers.knet.ui.desktop.codeeditor.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions

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

    /** Width used to estimate one monospace gutter digit. */
    val GutterDigitWidth = 8.dp

    /** Fixed space surrounding calculated gutter digits. */
    val GutterWidthPadding = 12.dp

    /** Editor container corner radius. */
    val ContainerCornerRadius = 6.dp

    /** Shared editor border width inherited from the KNet design system. */
    val BorderWidth = KNetDimensions.borderWidth

    /** Header bottom padding. */
    val HeaderBottomPadding = 6.dp

    /** Spacing between header actions. */
    val HeaderActionSpacing = 8.dp

    /** Header action corner radius. */
    val HeaderActionCornerRadius = 4.dp

    /** Horizontal padding for ordinary header actions. */
    val HeaderActionHorizontalPadding = 8.dp

    /** Compact horizontal padding for the fold-action group. */
    val FoldActionHorizontalPadding = 6.dp

    /** Vertical padding for header actions. */
    val HeaderActionVerticalPadding = 2.dp

    /** Compact header label size. */
    val HeaderFontSize = 10.sp

    /** Minimum draggable scrollbar thumb height. */
    val ScrollbarMinimumHeight = 24.dp

    /** Scrollbar thickness inherited from the KNet design system. */
    val ScrollbarThickness = KNetDimensions.scrollbarWidth

    /** Scrollbar thumb corner radius. */
    val ScrollbarCornerRadius = 4.dp

    /** Pointer hit-zone width reserved for a scrollbar. */
    val ScrollbarHitZoneWidth = KNetDimensions.iconSizeMedium

    /** Fold arrow hit-target width inherited from the KNet design system. */
    val FoldArrowHitTargetWidth = KNetDimensions.iconSizeMedium

    /** Fold arrow visual icon size. */
    val FoldArrowIconSize = 12.dp

    /** Placeholder start padding aligned with the empty editor gutter. */
    val PlaceholderStartPadding = 64.dp

    /** Placeholder top alignment correction. */
    val PlaceholderTopPadding = 1.dp

    /** Maximum width of the built-in find/replace panel. */
    val SearchPanelMaximumWidth = 480.dp

    /** Internal find/replace panel padding. */
    val SearchPanelPadding = 8.dp

    /** Gap between compact find/replace controls. */
    val SearchControlSpacing = 4.dp

    /** Compact find/replace field height. */
    val SearchFieldHeight = KNetDimensions.inputHeightStandard

    /** Preferred width of one find/replace text field. */
    val SearchFieldWidth = KNetDimensions.searchFieldMinWidth

    /** Compact find/replace action hit-target size. */
    val SearchActionSize = KNetDimensions.buttonHeightCompact

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
