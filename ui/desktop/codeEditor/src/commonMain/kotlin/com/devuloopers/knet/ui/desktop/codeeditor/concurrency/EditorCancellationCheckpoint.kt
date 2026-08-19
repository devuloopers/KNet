package com.devuloopers.knet.ui.desktop.codeeditor.concurrency

/**
 * Cooperative cancellation checkpoint for UI-neutral editor computations.
 *
 * Callers running syntax, folding, or search work in a coroutine can supply a checkpoint that
 * throws the platform's cancellation exception. Core algorithms invoke it at bounded intervals
 * without depending on a particular task or coroutine framework.
 */
fun interface EditorCancellationCheckpoint {
    /** Throws when the owning computation should stop; otherwise returns normally. */
    fun ensureActive()

    companion object {
        /** No-op checkpoint for direct synchronous callers. */
        val None: EditorCancellationCheckpoint = EditorCancellationCheckpoint {}
    }
}
