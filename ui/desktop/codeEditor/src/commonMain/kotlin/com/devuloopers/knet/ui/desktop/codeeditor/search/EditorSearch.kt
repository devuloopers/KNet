package com.devuloopers.knet.ui.desktop.codeeditor.search

import com.devuloopers.knet.ui.desktop.codeeditor.concurrency.EditorCancellationCheckpoint
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorEditKind
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorTextEdit
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession

/**
 * Search behavior applied by [EditorSearchEngine].
 *
 * @property query Literal text or regular expression to find.
 * @property matchCase Whether character case must match.
 * @property wholeWord Whether matches must be bounded by non-word characters.
 * @property useRegularExpression Whether [query] is interpreted as a regular expression.
 */
data class EditorSearchOptions(
    val query: String,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val useRegularExpression: Boolean = false
)

/**
 * Search failure that can be presented without throwing from the editor pipeline.
 */
sealed interface EditorSearchFailure {
    /**
     * The requested regular expression could not be compiled.
     *
     * @property description Platform-neutral failure description.
     */
    data class InvalidRegularExpression(val description: String) : EditorSearchFailure
}

/**
 * One search match in document coordinates.
 *
 * @property range Exact matched range.
 * @property matchedText Text present in the searched snapshot.
 */
data class EditorSearchMatch(
    val range: EditorRange,
    val matchedText: String
)

/**
 * Immutable result of searching one document version.
 *
 * @property documentVersion Version searched.
 * @property options Options used for the search.
 * @property matches Ordered matches in document order.
 * @property failure Search failure, or `null` for a valid result.
 */
data class EditorSearchResult(
    val documentVersion: Long,
    val options: EditorSearchOptions,
    val matches: List<EditorSearchMatch>,
    val failure: EditorSearchFailure? = null
)

/**
 * Stateless line-oriented search engine that never filters or rewrites displayed document lines.
 */
object EditorSearchEngine {
    /**
     * Finds matches in an immutable snapshot.
     *
     * Search is line-oriented so large documents do not require full-text serialization. Regular
     * expressions therefore do not span newline boundaries.
     *
     * @param snapshot Snapshot to search.
     * @param options Search behavior.
     * @param checkpoint Cooperative cancellation checkpoint invoked during bounded work.
     * @return Ordered matches or a typed regular-expression failure.
     */
    fun search(
        snapshot: EditorDocumentSnapshot,
        options: EditorSearchOptions,
        checkpoint: EditorCancellationCheckpoint = EditorCancellationCheckpoint.None
    ): EditorSearchResult {
        if (options.query.isEmpty()) return EditorSearchResult(snapshot.version, options, emptyList())
        return if (options.useRegularExpression) {
            searchRegex(snapshot, options, checkpoint)
        } else {
            searchLiteral(snapshot, options, checkpoint)
        }
    }

    private fun searchLiteral(
        snapshot: EditorDocumentSnapshot,
        options: EditorSearchOptions,
        checkpoint: EditorCancellationCheckpoint
    ): EditorSearchResult {
        val matches = buildList {
            for (lineIndex in 0 until snapshot.lineCount) {
                if (lineIndex % 256 == 0) checkpoint.ensureActive()
                val line = snapshot.line(lineIndex)
                var searchFrom = 0
                while (searchFrom <= line.length - options.query.length) {
                    val found = line.indexOf(options.query, searchFrom, ignoreCase = !options.matchCase)
                    if (found < 0) break
                    val end = found + options.query.length
                    if (!options.wholeWord || hasWholeWordBoundaries(line, found, end)) {
                        add(
                            EditorSearchMatch(
                                EditorRange(EditorPosition(lineIndex, found), EditorPosition(lineIndex, end)),
                                line.substring(found, end)
                            )
                        )
                    }
                    searchFrom = maxOf(end, found + 1)
                }
            }
        }
        return EditorSearchResult(snapshot.version, options, matches)
    }

    private fun searchRegex(
        snapshot: EditorDocumentSnapshot,
        options: EditorSearchOptions,
        checkpoint: EditorCancellationCheckpoint
    ): EditorSearchResult {
        val regex = try {
            val regexOptions = if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(options.query, regexOptions)
        } catch (exception: IllegalArgumentException) {
            return EditorSearchResult(
                documentVersion = snapshot.version,
                options = options,
                matches = emptyList(),
                failure = EditorSearchFailure.InvalidRegularExpression(exception.message ?: "Invalid regular expression.")
            )
        }
        val matches = buildList {
            for (lineIndex in 0 until snapshot.lineCount) {
                if (lineIndex % 256 == 0) checkpoint.ensureActive()
                val line = snapshot.line(lineIndex)
                regex.findAll(line).forEach { match ->
                    val start = match.range.first
                    val end = match.range.last + 1
                    if (!options.wholeWord || hasWholeWordBoundaries(line, start, end)) {
                        add(
                            EditorSearchMatch(
                                EditorRange(EditorPosition(lineIndex, start), EditorPosition(lineIndex, end)),
                                match.value
                            )
                        )
                    }
                }
            }
        }
        return EditorSearchResult(snapshot.version, options, matches)
    }

    private fun hasWholeWordBoundaries(line: String, start: Int, end: Int): Boolean {
        val beginsAtBoundary = start == 0 || !line[start - 1].isEditorWordCharacter()
        val endsAtBoundary = end == line.length || !line[end].isEditorWordCharacter()
        return beginsAtBoundary && endsAtBoundary
    }

    private fun Char.isEditorWordCharacter(): Boolean = isLetterOrDigit() || this == '_'
}

/**
 * Stateful navigation and replacement facade over immutable [EditorSearchResult] values.
 */
class EditorSearchSession {
    /** Latest search result, or `null` before the first search. */
    var result: EditorSearchResult? = null
        private set

    /** Zero-based active match index, or `-1` when no match is active. */
    var activeMatchIndex: Int = -1
        private set

    /** Active match, or `null` when the result is empty. */
    val activeMatch: EditorSearchMatch?
        get() = result?.matches?.getOrNull(activeMatchIndex)

    /**
     * Searches a snapshot and activates its first match.
     *
     * @param snapshot Snapshot to search.
     * @param options Search behavior.
     * @return New immutable result.
     */
    fun update(snapshot: EditorDocumentSnapshot, options: EditorSearchOptions): EditorSearchResult {
        result = EditorSearchEngine.search(snapshot, options)
        activeMatchIndex = if (result?.matches.isNullOrEmpty()) -1 else 0
        return requireNotNull(result)
    }

    /** Moves to the next match with wraparound. */
    fun next(): EditorSearchMatch? {
        val matches = result?.matches.orEmpty()
        if (matches.isEmpty()) return null
        activeMatchIndex = (activeMatchIndex + 1).mod(matches.size)
        return matches[activeMatchIndex]
    }

    /** Moves to the previous match with wraparound. */
    fun previous(): EditorSearchMatch? {
        val matches = result?.matches.orEmpty()
        if (matches.isEmpty()) return null
        activeMatchIndex = (activeMatchIndex - 1).mod(matches.size)
        return matches[activeMatchIndex]
    }

    /**
     * Replaces the active match in a session.
     *
     * @param session Target session whose version must match the search result.
     * @param replacement Replacement text.
     * @return `true` when a current-version match was replaced.
     */
    fun replaceCurrent(session: EditorSession, replacement: String): Boolean {
        val currentResult = result ?: return false
        val match = activeMatch ?: return false
        if (currentResult.documentVersion != session.snapshot.version) return false
        session.apply(EditorTextEdit(match.range, replacement, EditorEditKind.Replacement))
        update(session.snapshot, currentResult.options)
        return true
    }

    /**
     * Replaces every current match as one undoable batch.
     *
     * @param session Target session whose version must match the search result.
     * @param replacement Replacement text.
     * @return Number of replaced matches.
     */
    fun replaceAll(session: EditorSession, replacement: String): Int {
        val currentResult = result ?: return 0
        if (currentResult.documentVersion != session.snapshot.version || currentResult.matches.isEmpty()) return 0
        val edits = currentResult.matches.map { match ->
            EditorTextEdit(match.range, replacement, EditorEditKind.Structural)
        }
        session.applyBatch(edits)
        val replacementCount = edits.size
        update(session.snapshot, currentResult.options)
        return replacementCount
    }
}
