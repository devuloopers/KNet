package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * High-performance visibility calculation engine for virtualized code viewer lines.
 *
 * Filters uncollapsed lines and generates display text stubs for collapsed fold regions.
 */
public object LazyLineVisibilityEngine {

    /**
     * Builds the ordered list of [LazyLine] items to render in [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyReadOnlyBody].
     *
     * @param rawLines Complete list of document lines split off the UI thread.
     * @param foldRegions Calculated fold region blocks.
     * @param collapsedFoldStartLines Set of 0-indexed start line numbers currently collapsed.
     * @return List of visible [LazyLine] instances.
     */
    public fun buildVisibleLines(
        rawLines: List<String>,
        foldRegions: List<FoldRegion>,
        collapsedFoldStartLines: Set<Int>
    ): List<LazyLine> {
        if (foldRegions.isEmpty()) {
            return rawLines.mapIndexed { index, text ->
                LazyLine(
                    originalLineIndex = index,
                    displayText = text,
                    foldState = LineFoldState.None
                )
            }
        }

        val foldByStart = foldRegions.associateBy { it.startLine }
        val hiddenIndices = HashSet<Int>()

        for (startLine in collapsedFoldStartLines) {
            val region = foldByStart[startLine] ?: continue
            for (lineIndex in (startLine + 1)..region.endLine) {
                hiddenIndices.add(lineIndex)
            }
        }

        val visibleLines = ArrayList<LazyLine>(rawLines.size - hiddenIndices.size)

        for (index in rawLines.indices) {
            if (index in hiddenIndices) continue

            val foldRegion = foldByStart[index]
            val foldState = when {
                foldRegion == null -> LineFoldState.None
                index in collapsedFoldStartLines -> LineFoldState.FoldStartCollapsed
                else -> LineFoldState.FoldStartExpanded
            }

            val displayText = if (foldState is LineFoldState.FoldStartCollapsed && foldRegion != null) {
                rawLines[index].trimEnd() + " ... " + foldRegion.closingSymbol
            } else {
                rawLines[index]
            }

            visibleLines.add(
                LazyLine(
                    originalLineIndex = index,
                    displayText = displayText,
                    foldState = foldState,
                    foldRegion = foldRegion
                )
            )
        }

        return visibleLines
    }
}
