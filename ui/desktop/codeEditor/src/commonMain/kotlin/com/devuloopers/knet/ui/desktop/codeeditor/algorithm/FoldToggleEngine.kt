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

/**
 * Calculates the original document line number (1-indexed) for a given displayed line index,
 * delegating to [DocumentLayoutMap] for $O(1)$ constant-time resolution.
 *
 * @param displayedIndex 0-indexed line position in the currently displayed text.
 * @param layoutMap Active [DocumentLayoutMap] instance.
 * @return 1-indexed original document line number.
 */
fun getOriginalLineNumber(
    displayedIndex: Int,
    layoutMap: DocumentLayoutMap
): Int {
    return layoutMap.toDocumentLine(displayedIndex) + 1
}

/**
 * Legacy fallback overload for [getOriginalLineNumber].
 */
fun getOriginalLineNumber(
    displayedIndex: Int,
    collapsedFolds: Map<Int, CollapsedFoldState>
): Int {
    var hiddenBefore = 0
    for ((startLine, state) in collapsedFolds) {
        if (startLine < displayedIndex) {
            hiddenBefore += state.hiddenLines.size
        }
    }
    return displayedIndex + hiddenBefore + 1
}
