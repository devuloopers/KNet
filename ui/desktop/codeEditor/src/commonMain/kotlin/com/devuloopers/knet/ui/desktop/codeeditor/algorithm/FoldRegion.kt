package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * Immutable collapsible region in logical document coordinates.
 *
 * @property startLine Zero-based line containing the opening construct.
 * @property endLine Zero-based line containing the closing construct.
 * @property closingSymbol Short closing marker shown by a folded-line renderer.
 */
data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val closingSymbol: String = "}"
) {
    init {
        require(startLine >= 0) { "Fold start line must be non-negative." }
        require(endLine > startLine) { "Fold end line must follow its start line." }
    }
}
