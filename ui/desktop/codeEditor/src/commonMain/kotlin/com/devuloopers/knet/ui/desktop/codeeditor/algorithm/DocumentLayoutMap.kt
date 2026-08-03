package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.CollapsedFoldState

/**
 * Single Source of Truth for translating between original document line coordinates and displayed editor line coordinates.
 *
 * Provides $O(1)$ constant-time bi-directional lookups for all editor subsystems:
 * Gutter, Caret, Selection, Folding, Search, Diagnostics, Bookmarks, and Inline Widgets.
 *
 * Adheres to KNet UI Specification: Document Layout Mapping System v1.0.
 */
class DocumentLayoutMap private constructor(
    private val docToDisplayed: IntArray,
    private val displayedToDoc: IntArray,
    private val hiddenFlags: BooleanArray,
    val totalDocumentLines: Int,
    val visibleLineCount: Int
) {

    /**
     * Translates a 0-indexed raw document line number to its 0-indexed displayed line index.
     *
     * @param documentLine 0-indexed raw document line index.
     * @return 0-indexed displayed line index, or `null` if the line is currently hidden inside a collapsed fold block.
     */
    fun toDisplayedLine(documentLine: Int): Int? {
        if (documentLine !in 0 until totalDocumentLines) return null
        if (hiddenFlags[documentLine]) return null
        val idx = docToDisplayed[documentLine]
        return if (idx >= 0) idx else null
    }

    /**
     * Translates a 0-indexed displayed line index to its 0-indexed original document line number.
     *
     * @param displayedLine 0-indexed displayed line index in the editor view.
     * @return 0-indexed raw document line number.
     */
    fun toDocumentLine(displayedLine: Int): Int {
        if (visibleLineCount <= 0) return 0
        val safeIndex = displayedLine.coerceIn(0, visibleLineCount - 1)
        return displayedToDoc[safeIndex]
    }

    /**
     * Returns true if the given 0-indexed document line is hidden inside a collapsed fold block.
     */
    fun isHidden(documentLine: Int): Boolean {
        if (documentLine !in 0 until totalDocumentLines) return false
        return hiddenFlags[documentLine]
    }

    companion object {
        /**
         * Builds an immutable [DocumentLayoutMap] instance in $O(N)$ time.
         *
         * @param totalDocumentLines Total line count of the unfolded raw document.
         * @param collapsedFolds Map of displayed index -> CollapsedFoldState.
         * @return New [DocumentLayoutMap] instance.
         */
        fun build(
            totalDocumentLines: Int,
            collapsedFolds: Map<Int, CollapsedFoldState>
        ): DocumentLayoutMap {
            if (totalDocumentLines <= 0) {
                return DocumentLayoutMap(IntArray(0), IntArray(0), BooleanArray(0), 0, 0)
            }

            val docToDisplayed = IntArray(totalDocumentLines) { -1 }
            val hiddenFlags = BooleanArray(totalDocumentLines) { false }

            val sortedFolds = collapsedFolds.entries.sortedBy { it.key }

            var currentDocLine = 0
            var currentDisplayedIndex = 0

            val displayedToDocList = IntArray(totalDocumentLines)

            for ((dispLine, state) in sortedFolds) {
                // Advance uncollapsed lines up to dispLine
                while (currentDisplayedIndex < dispLine && currentDocLine < totalDocumentLines) {
                    docToDisplayed[currentDocLine] = currentDisplayedIndex
                    displayedToDocList[currentDisplayedIndex] = currentDocLine
                    currentDocLine++
                    currentDisplayedIndex++
                }

                // Match fold header line at dispLine
                if (currentDocLine < totalDocumentLines) {
                    docToDisplayed[currentDocLine] = currentDisplayedIndex
                    displayedToDocList[currentDisplayedIndex] = currentDocLine
                    currentDocLine++
                    currentDisplayedIndex++
                }

                // Mark hidden lines
                val hiddenCount = state.hiddenLines.size
                repeat(hiddenCount) {
                    if (currentDocLine < totalDocumentLines) {
                        hiddenFlags[currentDocLine] = true
                        docToDisplayed[currentDocLine] = -1
                        currentDocLine++
                    }
                }
            }

            // Advance remaining document lines
            while (currentDocLine < totalDocumentLines) {
                docToDisplayed[currentDocLine] = currentDisplayedIndex
                displayedToDocList[currentDisplayedIndex] = currentDocLine
                currentDocLine++
                currentDisplayedIndex++
            }

            val visibleCount = currentDisplayedIndex
            val displayedToDoc = displayedToDocList.copyOf(visibleCount)

            return DocumentLayoutMap(
                docToDisplayed = docToDisplayed,
                displayedToDoc = displayedToDoc,
                hiddenFlags = hiddenFlags,
                totalDocumentLines = totalDocumentLines,
                visibleLineCount = visibleCount
            )
        }
    }
}
