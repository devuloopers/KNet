package com.devuloopers.knet.ui.desktop.codeeditor.algorithm


/**
 * Pure engine calculating 0-indexed column range boundaries for single word selection,
 * punctuation symbols, and whitespace sequences.
 *
 * Implements VS Code standard character classification rules (`characterClassifier.ts`).
 */
object WordBoundaryEngine {

    private enum class CharacterClass {
        WORD,
        PUNCTUATION,
        WHITESPACE
    }

    /**
     * Calculates the start and end column range for word selection at [colIndex] on [lineText].
     *
     * @param lineText Text content of the line.
     * @param colIndex 0-indexed column position of the double click.
     * @return [Pair] of 0-indexed `(startCol, endCol)` bounds.
     */
    fun findWordBounds(lineText: String, colIndex: Int): Pair<Int, Int> {
        if (lineText.isEmpty()) return 0 to 0

        val safeCol = colIndex.coerceIn(0, lineText.length)
        val targetCol = if (safeCol == lineText.length && lineText.isNotEmpty()) {
            safeCol - 1
        } else {
            safeCol
        }

        val targetChar = lineText[targetCol]
        val targetClass = classifyChar(targetChar)

        var startCol = targetCol
        while (startCol > 0 && classifyChar(lineText[startCol - 1]) == targetClass) {
            startCol--
        }

        var endCol = targetCol
        while (endCol < lineText.length && classifyChar(lineText[endCol]) == targetClass) {
            endCol++
        }

        return startCol to endCol
    }

    /**
     * Classifies a character into VS Code's 3 standard character categories.
     */
    private fun classifyChar(ch: Char): CharacterClass {
        return when {
            ch.isLetterOrDigit() || ch == '_' || ch == '$' -> CharacterClass.WORD
            ch.isWhitespace() -> CharacterClass.WHITESPACE
            else -> CharacterClass.PUNCTUATION
        }
    }
}

