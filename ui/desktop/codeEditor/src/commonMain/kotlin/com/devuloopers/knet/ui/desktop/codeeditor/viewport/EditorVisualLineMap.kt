package com.devuloopers.knet.ui.desktop.codeeditor.viewport

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LineFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot

/**
 * Immutable mapping between logical document lines and virtualized visual rows.
 *
 * The identity case allocates no per-line arrays. Arrays are created only while folds are collapsed,
 * keeping ordinary large-document scrolling proportional to visible Compose rows.
 */
internal class EditorVisualLineMap private constructor(
    /** Total logical document line count. */
    val documentLineCount: Int,
    private val visibleToDocument: IntArray?,
    private val documentToVisible: IntArray?,
    private val foldByStart: Map<Int, FoldRegion>,
    private val collapsedStarts: Set<Int>
) {
    /** Number of virtualized visual rows. */
    val visibleLineCount: Int
        get() = visibleToDocument?.size ?: documentLineCount

    /**
     * Maps a visual row to a logical document line.
     *
     * @param visualLine Zero-based visual row.
     * @return Zero-based logical line.
     */
    fun toDocumentLine(visualLine: Int): Int {
        require(visualLine in 0 until visibleLineCount) { "Visual line is outside the viewport map." }
        return visibleToDocument?.get(visualLine) ?: visualLine
    }

    /**
     * Maps a logical line to its visible row.
     *
     * @param documentLine Zero-based logical line.
     * @return Visible row, or `null` when hidden by a collapsed fold.
     */
    fun toVisibleLine(documentLine: Int): Int? {
        if (documentLine !in 0 until documentLineCount) return null
        val mapped = documentToVisible?.get(documentLine) ?: documentLine
        return mapped.takeIf { it >= 0 }
    }

    /**
     * Creates a renderable line descriptor on demand without materializing every document line.
     *
     * @param snapshot Source snapshot.
     * @param visualLine Visual row to describe.
     * @return Renderable logical line and fold state.
     */
    fun lazyLine(snapshot: EditorDocumentSnapshot, visualLine: Int): LazyLine {
        val documentLine = toDocumentLine(visualLine)
        val fold = foldByStart[documentLine]
        val foldState = when {
            fold == null -> LineFoldState.None
            documentLine in collapsedStarts -> LineFoldState.FoldStartCollapsed
            else -> LineFoldState.FoldStartExpanded
        }
        val sourceText = snapshot.line(documentLine)
        val displayText = if (foldState == LineFoldState.FoldStartCollapsed && fold != null) {
            sourceText.trimEnd() + " ... " + fold.closingSymbol
        } else {
            sourceText
        }
        return LazyLine(documentLine, displayText, foldState, fold)
    }

    companion object {
        /**
         * Builds a mapping for current fold state.
         *
         * @param documentLineCount Total logical lines.
         * @param foldRegions Available fold regions.
         * @param collapsedStarts Fold-start lines currently collapsed.
         * @return Immutable identity or collapsed mapping.
         */
        fun build(
            documentLineCount: Int,
            foldRegions: List<FoldRegion>,
            collapsedStarts: Set<Int>
        ): EditorVisualLineMap {
            require(documentLineCount > 0) { "Editor document must contain at least one line." }
            val foldByStart = foldRegions.associateBy(FoldRegion::startLine)
            val activeFolds = collapsedStarts.mapNotNull(foldByStart::get).sortedBy(FoldRegion::startLine)
            if (activeFolds.isEmpty()) {
                return EditorVisualLineMap(documentLineCount, null, null, foldByStart, emptySet())
            }

            val hidden = BooleanArray(documentLineCount)
            for (fold in activeFolds) {
                val end = fold.endLine.coerceAtMost(documentLineCount - 1)
                for (line in (fold.startLine + 1)..end) hidden[line] = true
            }
            val visibleToDocument = IntArray(hidden.count { isHidden -> !isHidden })
            val documentToVisible = IntArray(documentLineCount) { -1 }
            var visibleIndex = 0
            for (documentLine in 0 until documentLineCount) {
                if (!hidden[documentLine]) {
                    visibleToDocument[visibleIndex] = documentLine
                    documentToVisible[documentLine] = visibleIndex
                    visibleIndex++
                }
            }
            return EditorVisualLineMap(
                documentLineCount,
                visibleToDocument,
                documentToVisible,
                foldByStart,
                activeFolds.mapTo(mutableSetOf(), FoldRegion::startLine)
            )
        }
    }
}
