package com.devuloopers.knet.ui.desktop.codeeditor.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorEditKind
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorTextEdit
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchMatch
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchOptions
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchResult
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSessionEvent

/**
 * Compose-observable adapter around a UI-neutral [EditorSession].
 *
 * The session remains the only mutation owner. This adapter exposes immutable snapshots to Compose
 * without copying every document line into mutable UI collections.
 *
 * @param session UI-neutral editor session to observe.
 */
@Stable
class CodeEditorState(
    session: EditorSession
) {
    /** UI-neutral mutation and history owner observed by this Compose state. */
    val session: EditorSession = session

    /** Latest immutable document snapshot. */
    var snapshot: EditorDocumentSnapshot by mutableStateOf(session.snapshot)
        private set

    /** Latest caret position. */
    var caret: EditorPosition by mutableStateOf(session.caret)
        private set

    /** Latest directional selection, or `null`. */
    var selection: EditorSelection? by mutableStateOf(session.selection)
        private set

    /** Latest session transition, or `null` before the first transition. */
    var latestEvent: EditorSessionEvent? by mutableStateOf(null)
        private set

    /** Latest non-destructive search result, or `null` when search is inactive. */
    var searchResult: EditorSearchResult? by mutableStateOf(null)
        private set

    /** Zero-based active search-match index, or `-1` when no result is active. */
    var activeSearchMatchIndex: Int by mutableStateOf(-1)
        private set

    /** Whether the built-in find/replace surface is visible. */
    var isSearchVisible: Boolean by mutableStateOf(false)
        private set

    /** Current find options owned by this editor instance. */
    var searchOptions: EditorSearchOptions by mutableStateOf(EditorSearchOptions(query = ""))
        private set

    /** Current replacement text owned by this editor instance. */
    var searchReplacement: String by mutableStateOf("")
        private set

    /** Active search match, or `null` when search has no matches. */
    val activeSearchMatch: EditorSearchMatch?
        get() = searchResult?.matches?.getOrNull(activeSearchMatchIndex)

    private val subscription = session.subscribe { event ->
        snapshot = event.snapshot
        caret = event.caret
        selection = event.selection
        latestEvent = event
    }

    /**
     * Replaces externally controlled text without adding an undo entry.
     *
     * @param text New complete text.
     */
    fun replaceFromExternal(text: String) {
        session.replaceAllFromExternal(text)
    }

    /**
     * Explicitly serializes the current complete document.
     *
     * @return Current full text.
     */
    fun text(): String = snapshot.text()

    /** Opens the built-in find/replace surface using existing options. */
    fun openSearch() {
        isSearchVisible = true
    }

    /**
     * Opens the built-in find/replace surface with [options].
     *
     * @param options Initial or externally supplied search behavior.
     */
    fun openSearch(options: EditorSearchOptions) {
        searchOptions = options
        isSearchVisible = true
    }

    /** Closes find/replace and clears its result projection without changing the document. */
    fun closeSearch() {
        isSearchVisible = false
        updateSearchResult(null)
    }

    /**
     * Updates find behavior while preserving the search surface.
     *
     * @param options New search options.
     */
    fun updateSearchOptions(options: EditorSearchOptions) {
        searchOptions = options
    }

    /**
     * Updates replacement text without modifying the document.
     *
     * @param replacement New replacement text.
     */
    fun updateSearchReplacement(replacement: String) {
        searchReplacement = replacement
    }

    /** Moves to and selects the next search match with wraparound. */
    fun nextSearchMatch(): EditorSearchMatch? {
        val matches = searchResult?.matches.orEmpty()
        if (matches.isEmpty()) return null
        activeSearchMatchIndex = (activeSearchMatchIndex + 1).mod(matches.size)
        return selectActiveSearchMatch()
    }

    /** Moves to and selects the previous search match with wraparound. */
    fun previousSearchMatch(): EditorSearchMatch? {
        val matches = searchResult?.matches.orEmpty()
        if (matches.isEmpty()) return null
        activeSearchMatchIndex = (activeSearchMatchIndex - 1).mod(matches.size)
        return selectActiveSearchMatch()
    }

    /**
     * Replaces the active current-version search match.
     *
     * @param replacement Replacement text.
     * @return `true` when a match was replaced.
     */
    fun replaceActiveSearchMatch(replacement: String): Boolean {
        val result = searchResult ?: return false
        val match = activeSearchMatch ?: return false
        if (result.documentVersion != snapshot.version) return false
        session.apply(EditorTextEdit(match.range, replacement, EditorEditKind.Replacement))
        return true
    }

    /**
     * Replaces all current-version search matches as one undoable batch.
     *
     * @param replacement Replacement text.
     * @return Number of replaced matches.
     */
    fun replaceAllSearchMatches(replacement: String): Int {
        val result = searchResult ?: return 0
        if (result.documentVersion != snapshot.version) return 0
        val edits = result.matches.map { match ->
            EditorTextEdit(match.range, replacement, EditorEditKind.Structural)
        }
        session.applyBatch(edits)
        return edits.size
    }

    internal fun updateSearchResult(result: EditorSearchResult?) {
        val previousRange = activeSearchMatch?.range
        searchResult = result
        activeSearchMatchIndex = when {
            result == null || result.matches.isEmpty() -> -1
            previousRange == null -> 0
            else -> result.matches.indexOfFirst { it.range == previousRange }.takeIf { it >= 0 } ?: 0
        }
        if (isSearchVisible && activeSearchMatchIndex >= 0) selectActiveSearchMatch()
    }

    private fun selectActiveSearchMatch(): EditorSearchMatch? {
        val match = activeSearchMatch ?: return null
        session.select(EditorSelection(match.range.start, match.range.end))
        return match
    }

    /** Releases the internal session observer when a manually managed state is discarded. */
    fun close() {
        subscription.cancel()
    }
}

/**
 * Creates a remembered editor state initialized with [initialText].
 *
 * External value synchronization is intentionally performed by the controlling editor facade so
 * callers using the stateful API retain explicit ownership of document replacement.
 *
 * @param initialText Initial complete document text.
 * @return Remembered Compose editor state.
 */
@Composable
fun rememberCodeEditorState(initialText: String = ""): CodeEditorState {
    val state = remember { CodeEditorState(EditorSession(initialText)) }
    DisposableEffect(state) {
        onDispose(state::close)
    }
    return state
}
