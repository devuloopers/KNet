package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * Single state snapshot entry in the undo/redo stack.
 *
 * @property text The document text content.
 * @property timestamp System epoch timestamp when edit occurred.
 */
data class TextEditSnapshot(
    val text: String,
    val timestamp: Long = currentSystemTimeMillis()
)

/**
 * Expect/actual time provider or fallback timestamp helper for multiplatform usage.
 */
internal fun currentSystemTimeMillis(): Long {
    return try {
        System.currentTimeMillis()
    } catch (_: Throwable) {
        0L
    }
}

/**
 * A compound edit history stack for code editing with automatic typing burst coalescing.
 * Inspired by RSyntaxTextArea RUndoManager.
 */
class UndoRedoStack(
    private val maxStackSize: Int = 100,
    private val coalesceWindowMs: Long = 500L
) {
    private val history = mutableListOf<TextEditSnapshot>()
    private var pointer = -1

    val canUndo: Boolean
        get() = pointer > 0

    val canRedo: Boolean
        get() = pointer < history.lastIndex

    /**
     * Initializes stack with starting text content.
     */
    fun init(initialText: String) {
        history.clear()
        history.add(TextEditSnapshot(initialText))
        pointer = 0
    }

    /**
     * Pushes a new document text mutation state into the undo stack.
     * Merges rapid single-character typings within [coalesceWindowMs].
     */
    fun push(newText: String) {
        if (pointer >= 0 && pointer < history.size && history[pointer].text == newText) {
            return
        }

        val currentTime = currentSystemTimeMillis()

        // Check if we can coalesce with previous edit
        if (pointer > 0 && pointer == history.lastIndex) {
            val last = history[pointer]
            val timeDiff = currentTime - last.timestamp
            val lengthDiff = kotlin.math.abs(newText.length - last.text.length)

            // Coalesce rapid single-character insertions/deletions
            if (timeDiff in 1 until coalesceWindowMs && lengthDiff == 1) {
                history[pointer] = TextEditSnapshot(newText, currentTime)
                return
            }
        }

        // Truncate any redo entries ahead of pointer
        while (history.size > pointer + 1) {
            history.removeAt(history.lastIndex)
        }

        history.add(TextEditSnapshot(newText, currentTime))

        // Enforce maximum stack depth
        if (history.size > maxStackSize) {
            history.removeAt(0)
        } else {
            pointer++
        }
    }

    /**
     * Reverts to previous text state in stack.
     * @return The previous text string, or `null` if cannot undo.
     */
    fun undo(): String? {
        if (!canUndo) return null
        pointer--
        return history[pointer].text
    }

    /**
     * Re-applies next text state in stack.
     * @return The next text string, or `null` if cannot redo.
     */
    fun redo(): String? {
        if (!canRedo) return null
        pointer++
        return history[pointer].text
    }
}
