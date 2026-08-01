package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.model.FoldRegion

internal data class CollapsedFoldState(
    val startLine: Int,
    val endLine: Int,
    val originalHeader: String,
    val hiddenLines: List<String>,
    val closingSymbol: String
)

internal fun performFoldToggle(
    lineIndex: Int,
    displayedText: String,
    collapsedFolds: Map<Int, CollapsedFoldState>,
    foldStartLines: Map<Int, FoldRegion>,
    onCollapsedFoldsChange: (Map<Int, CollapsedFoldState>) -> Unit
): String {
    val existingFold = collapsedFolds[lineIndex]
    val displayedLines = displayedText.lines().toMutableList()

    if (existingFold != null) {
        // Expand
        val newFolds = collapsedFolds - lineIndex
        onCollapsedFoldsChange(newFolds)
        displayedLines[lineIndex] = existingFold.originalHeader
        displayedLines.addAll(lineIndex + 1, existingFold.hiddenLines)
        return displayedLines.joinToString("\n")
    }

    val region = foldStartLines[lineIndex] ?: return displayedText
    val hiddenCount = region.endLine - region.startLine
    if (hiddenCount <= 0 || lineIndex + hiddenCount >= displayedLines.size) return displayedText

    val originalHeader = displayedLines[lineIndex]
    val hiddenLines = displayedLines.subList(lineIndex + 1, lineIndex + 1 + hiddenCount).toList()

    val collapsedState = CollapsedFoldState(
        startLine = region.startLine,
        endLine = region.endLine,
        originalHeader = originalHeader,
        hiddenLines = hiddenLines,
        closingSymbol = region.closingSymbol
    )

    displayedLines[lineIndex] = "$originalHeader ... ${region.closingSymbol}"
    for (i in 0 until hiddenCount) {
        displayedLines.removeAt(lineIndex + 1)
    }

    onCollapsedFoldsChange(collapsedFolds + (lineIndex to collapsedState))
    return displayedLines.joinToString("\n")
}

internal fun collapseAllFolds(fullText: String): Pair<String, Map<Int, CollapsedFoldState>> {
    val lines = fullText.lines()
    val foldRegions = FoldManager.calculateFolds(lines)
    if (foldRegions.isEmpty()) return fullText to emptyMap()

    val foldMap = foldRegions.sortedByDescending { it.startLine }
    val newFolds = mutableMapOf<Int, CollapsedFoldState>()
    val resultLines = lines.toMutableList()

    for (region in foldMap) {
        val start = region.startLine
        val count = region.endLine - region.startLine
        if (start in resultLines.indices && start + count < resultLines.size) {
            val originalHeader = resultLines[start]
            val hidden = resultLines.subList(start + 1, start + 1 + count).toList()

            resultLines[start] = "$originalHeader ... ${region.closingSymbol}"
            for (i in 0 until count) {
                resultLines.removeAt(start + 1)
            }

            newFolds[start] = CollapsedFoldState(
                startLine = start,
                endLine = region.endLine,
                originalHeader = originalHeader,
                hiddenLines = hidden,
                closingSymbol = region.closingSymbol
            )
        }
    }

    return resultLines.joinToString("\n") to newFolds
}

internal fun getOriginalLineNumber(
    displayedLineIndex: Int,
    collapsedFolds: Map<Int, CollapsedFoldState>
): Int {
    if (collapsedFolds.isEmpty()) return displayedLineIndex + 1
    var skipped = 0
    collapsedFolds.entries.sortedBy { it.key }.forEach { (start, state) ->
        if (displayedLineIndex > start) {
            skipped += state.hiddenLines.size
        }
    }
    return displayedLineIndex + 1 + skipped
}
