package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * Data model storing uncollapsed state details for a folded code region.
 *
 * @property originalHeader Uncollapsed original line text.
 * @property hiddenLines Hidden body lines belonging to the fold region.
 */
data class CollapsedFoldState(
    val originalHeader: String,
    val hiddenLines: List<String>
)
