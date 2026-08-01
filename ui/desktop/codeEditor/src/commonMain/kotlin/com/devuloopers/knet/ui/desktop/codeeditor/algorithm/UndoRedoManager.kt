package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * High-performance Undo/Redo stack manager for editable code sessions.
 */
internal class UndoRedoManager(private val maxCapacity: Int = 100) {

    private val stack = ArrayList<String>()
    private var currentIndex = -1

    fun init(initialText: String) {
        stack.clear()
        stack.add(initialText)
        currentIndex = 0
    }

    fun push(text: String) {
        if (currentIndex in stack.indices && stack[currentIndex] == text) return

        while (stack.size > currentIndex + 1) {
            stack.removeAt(stack.lastIndex)
        }

        stack.add(text)
        if (stack.size > maxCapacity) {
            stack.removeAt(0)
        } else {
            currentIndex++
        }
    }

    fun canUndo(): Boolean = currentIndex > 0

    fun canRedo(): Boolean = currentIndex < stack.lastIndex

    fun undo(): String? {
        if (!canUndo()) return null
        currentIndex--
        return stack[currentIndex]
    }

    fun redo(): String? {
        if (!canRedo()) return null
        currentIndex++
        return stack[currentIndex]
    }
}
