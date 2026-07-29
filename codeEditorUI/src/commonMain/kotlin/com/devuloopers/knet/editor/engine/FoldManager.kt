package com.devuloopers.knet.editor.engine

/**
 * Represents a collapsible block region in a code document.
 *
 * @property startLine The 0-indexed line where the fold starts (e.g. line containing '{' or '[').
 * @property endLine The 0-indexed line where the fold ends (e.g. line containing '}' or ']').
 * @property closingSymbol The character or string token indicating closing (e.g. "}", "]").
 */
data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val closingSymbol: String = "}"
)

/**
 * High-performance hierarchical code fold calculation and visual line mapping engine.
 * Inspired by RSyntaxTextArea FoldManagerImpl.
 */
object FoldManager {

    /** Maximum line limit threshold for deep AST character-by-character fold scanning. */
    const val MAX_FOLD_LINE_THRESHOLD = 5000

    private val foldCache = object : LinkedHashMap<Int, List<FoldRegion>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<FoldRegion>>?): Boolean {
            return size > 32
        }
    }

    /**
     * Calculates nested foldable regions for structured code documents (JSON, XML, JS, Kotlin).
     * Memoizes results by line hash and respects [MAX_FOLD_LINE_THRESHOLD] to prevent main-thread freezing.
     *
     * @param lines The list of text lines in the document.
     * @return List of calculated [FoldRegion] instances.
     */
    fun calculateFolds(lines: List<String>): List<FoldRegion> {
        if (lines.size <= 1 || lines.size > MAX_FOLD_LINE_THRESHOLD) {
            return emptyList()
        }

        val cacheKey = lines.hashCode()
        synchronized(foldCache) {
            val cached = foldCache[cacheKey]
            if (cached != null) return cached
        }

        val folds = mutableListOf<FoldRegion>()
        val braceStack = mutableListOf<Int>()
        val bracketStack = mutableListOf<Int>()

        for (index in lines.indices) {
            val line = lines[index]
            for (char in line) {
                when (char) {
                    '{' -> braceStack.add(index)
                    '}' -> {
                        if (braceStack.isNotEmpty()) {
                            val start = braceStack.removeAt(braceStack.lastIndex)
                            if (index > start) {
                                folds.add(FoldRegion(startLine = start, endLine = index, closingSymbol = "}"))
                            }
                        }
                    }
                    '[' -> bracketStack.add(index)
                    ']' -> {
                        if (bracketStack.isNotEmpty()) {
                            val start = bracketStack.removeAt(bracketStack.lastIndex)
                            if (index > start) {
                                folds.add(FoldRegion(startLine = start, endLine = index, closingSymbol = "]"))
                            }
                        }
                    }
                }
            }
        }

        synchronized(foldCache) {
            foldCache[cacheKey] = folds
        }

        return folds
    }

    /**
     * Clears all memoized fold calculation cache entries.
     */
    fun clearCache() {
        synchronized(foldCache) {
            foldCache.clear()
        }
    }

    /**
     * Maps virtualized LazyColumn visual item indices to 0-indexed document model line numbers,
     * skipping lines contained within active collapsed folds.
     *
     * @param totalLines Total number of lines in the document.
     * @param collapsedStartLines Set of 0-indexed start line indices that are currently collapsed.
     * @param foldRegions List of all available [FoldRegion] blocks.
     * @return List of document line indices to render.
     */
    fun buildVisualLineMap(
        totalLines: Int,
        collapsedStartLines: Set<Int>,
        foldRegions: List<FoldRegion>
    ): List<Int> {
        if (collapsedStartLines.isEmpty()) {
            return (0 until totalLines).toList()
        }

        // Map startLine -> endLine for active collapsed folds
        val activeCollapsedMap = foldRegions
            .filter { it.startLine in collapsedStartLines }
            .associate { it.startLine to it.endLine }

        val visibleLines = mutableListOf<Int>()
        var lineIndex = 0

        while (lineIndex < totalLines) {
            visibleLines.add(lineIndex)
            val endFold = activeCollapsedMap[lineIndex]
            if (endFold != null && endFold > lineIndex) {
                // Skip hidden lines inside collapsed fold
                lineIndex = endFold
            } else {
                lineIndex++
            }
        }

        return visibleLines
    }
}
