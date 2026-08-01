package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.model.FoldRegion

/**
 * High-performance hierarchical code fold calculation and visual line mapping engine.
 */
internal object FoldManager {

    /** Maximum line limit threshold for deep AST character-by-character fold scanning. */
    const val MAX_FOLD_LINE_THRESHOLD = 5000

    private val foldCache = object : LinkedHashMap<Int, List<FoldRegion>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<FoldRegion>>?): Boolean {
            return size > 32
        }
    }

    /**
     * Calculates nested foldable regions for structured code documents (JSON, XML, JS, Kotlin).
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
     * Maps virtualized LazyColumn visual item indices to 0-indexed document model line numbers.
     */
    fun buildVisualLineMap(
        totalLines: Int,
        collapsedStartLines: Set<Int>,
        foldRegions: List<FoldRegion>
    ): List<Int> {
        if (collapsedStartLines.isEmpty()) {
            return (0 until totalLines).toList()
        }

        val activeCollapsedMap = foldRegions
            .filter { it.startLine in collapsedStartLines }
            .associate { it.startLine to it.endLine }

        val visibleLines = mutableListOf<Int>()
        var lineIndex = 0

        while (lineIndex < totalLines) {
            visibleLines.add(lineIndex)
            val endFold = activeCollapsedMap[lineIndex]
            if (endFold != null && endFold > lineIndex) {
                lineIndex = endFold
            } else {
                lineIndex++
            }
        }

        return visibleLines
    }
}
