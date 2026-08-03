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
 * Formats a collapsed header line with inline ellipsis brackets (e.g. `{ ... }` or `[ ... ]`), preserving trailing commas.
 *
 * @param headerLine Original line text (e.g., `"queryParams": {`).
 * @param closingSymbol Symbol used to close the fold (e.g. `}` or `]`).
 * @param hiddenLines List of hidden lines in the fold region.
 * @return Formatted inline header string.
 */
fun formatInlineFoldHeader(
    headerLine: String,
    closingSymbol: String,
    hiddenLines: List<String>
): String {
    val hasTrailingComma = hiddenLines.lastOrNull()?.trim()?.endsWith(",") == true
    val commaSuffix = if (hasTrailingComma) "," else ""
    val trimmedHeader = headerLine.trimEnd()

    return when {
        closingSymbol == "}" && trimmedHeader.endsWith("{") -> {
            "${trimmedHeader} ... }$commaSuffix"
        }
        closingSymbol == "]" && trimmedHeader.endsWith("[") -> {
            "${trimmedHeader} ... ]$commaSuffix"
        }
        else -> {
            "$headerLine ... $closingSymbol$commaSuffix"
        }
    }
}

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

/**
 * Toggles line fold expansion or collapse state for a target line index.
 *
 * @param lineIndex 0-indexed displayed line position to toggle.
 * @param displayedText Current text content in the text field.
 * @param collapsedFolds Active map of collapsed fold states.
 * @param foldStartLines Map of displayed start line to [FoldRegion].
 * @param onCollapsedFoldsChange Callback to report updated collapsed fold map.
 * @return Updated displayed text with inline fold applied or removed.
 */
fun performFoldToggle(
    lineIndex: Int,
    displayedText: String,
    collapsedFolds: Map<Int, CollapsedFoldState>,
    foldStartLines: Map<Int, FoldRegion>,
    onCollapsedFoldsChange: (Map<Int, CollapsedFoldState>) -> Unit
): String {
    val currentLines = displayedText.lines().toMutableList()

    if (collapsedFolds.containsKey(lineIndex)) {
        val state = collapsedFolds[lineIndex] ?: return displayedText
        currentLines[lineIndex] = state.originalHeader
        currentLines.addAll(lineIndex + 1, state.hiddenLines)

        val offset = state.hiddenLines.size
        val newFolds = mutableMapOf<Int, CollapsedFoldState>()
        collapsedFolds.forEach { (key, value) ->
            when {
                key == lineIndex -> {}
                key > lineIndex -> newFolds[key + offset] = value
                else -> newFolds[key] = value
            }
        }
        onCollapsedFoldsChange(newFolds)
    } else {
        val foldRegion = foldStartLines[lineIndex] ?: return displayedText
        val lineSpan = foldRegion.endLine - foldRegion.startLine
        val endLine = lineIndex + lineSpan
        if (endLine <= lineIndex || endLine >= currentLines.size) return displayedText

        val originalHeader = currentLines[lineIndex]
        val hiddenLines = currentLines.subList(lineIndex + 1, endLine + 1).toList()
        val inlineHeader = formatInlineFoldHeader(originalHeader, foldRegion.closingSymbol, hiddenLines)

        val hiddenCount = hiddenLines.size
        currentLines[lineIndex] = inlineHeader
        repeat(hiddenCount) { currentLines.removeAt(lineIndex + 1) }

        val newFolds = mutableMapOf<Int, CollapsedFoldState>()
        collapsedFolds.forEach { (key, value) ->
            if (key > lineIndex) newFolds[key - hiddenCount] = value
            else newFolds[key] = value
        }
        newFolds[lineIndex] = CollapsedFoldState(originalHeader, hiddenLines)
        onCollapsedFoldsChange(newFolds)
    }

    return currentLines.joinToString("\n")
}

/**
 * Calculates a complete collapse of all top-level fold blocks for a document.
 */
fun collapseAllFolds(fullText: String): Pair<String, Map<Int, CollapsedFoldState>> {
    val fullLines = fullText.lines()
    if (fullLines.size <= 1) return fullText to emptyMap()
    val allFolds = FoldManager.calculateFolds(fullLines)
    if (allFolds.isEmpty()) return fullText to emptyMap()

    val outerFolds = allFolds.filter { region ->
        allFolds.none { other -> other.startLine < region.startLine && other.endLine > region.endLine }
    }.sortedBy { it.startLine }

    if (outerFolds.isEmpty()) return fullText to emptyMap()

    val displayedLines = mutableListOf<String>()
    val collapsedMap = mutableMapOf<Int, CollapsedFoldState>()
    var fullIndex = 0

    for (region in outerFolds) {
        if (region.startLine > fullIndex) {
            displayedLines.addAll(fullLines.subList(fullIndex, region.startLine))
        }
        val originalHeader = fullLines[region.startLine]
        val hidden = fullLines.subList(region.startLine + 1, region.endLine + 1)
        val inlineHeader = formatInlineFoldHeader(originalHeader, region.closingSymbol, hidden)

        displayedLines.add(inlineHeader)
        val displayedStartLine = displayedLines.size - 1
        collapsedMap[displayedStartLine] = CollapsedFoldState(originalHeader, hidden)
        fullIndex = region.endLine + 1
    }

    if (fullIndex < fullLines.size) {
        displayedLines.addAll(fullLines.subList(fullIndex, fullLines.size))
    }

    return displayedLines.joinToString("\n") to collapsedMap
}
