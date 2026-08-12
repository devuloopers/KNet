package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * State representation for code folding capabilities of a document line.
 */
public sealed interface LineFoldState {
    /**
     * Line has no fold region start.
     */
    public data object None : LineFoldState

    /**
     * Line starts a fold region and is currently expanded.
     */
    public data object FoldStartExpanded : LineFoldState

    /**
     * Line starts a fold region and is currently collapsed.
     */
    public data object FoldStartCollapsed : LineFoldState
}

/**
 * Renderable line model for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 *
 * @property originalLineIndex 0-indexed position in the original raw lines list.
 * @property displayText Text string to display (either raw line or collapsed stub such as `{ ... }`).
 * @property foldState Folding state of this line (none, expanded, collapsed).
 * @property foldRegion Associated [FoldRegion] if this line starts a fold region.
 */
public data class LazyLine(
    val originalLineIndex: Int,
    val displayText: String,
    val foldState: LineFoldState,
    val foldRegion: FoldRegion? = null
)
