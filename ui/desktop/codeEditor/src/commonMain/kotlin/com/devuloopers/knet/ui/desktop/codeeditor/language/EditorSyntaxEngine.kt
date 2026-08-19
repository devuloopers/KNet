package com.devuloopers.knet.ui.desktop.codeeditor.language

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentChange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.concurrency.EditorCancellationCheckpoint
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

private const val TOKEN_LINE_CHUNK_SIZE = 256
private const val MAX_IMMEDIATE_PRESENTATION_LINES = 32
private const val MAX_IMMEDIATE_PRESENTATION_CHARACTERS = 32 * 1024

internal data class EditorTokenLineChunk(val lines: List<EditorTokenizedLine>)

/**
 * Immutable semantic-token model for one document version and language.
 *
 * @property snapshot Source snapshot. Retained for exact incremental convergence checks.
 * @property language Language used for tokenization.
 */
class EditorTokenizedDocument private constructor(
    val snapshot: EditorDocumentSnapshot,
    val language: CodeLanguage,
    internal val chunks: List<EditorTokenLineChunk>,
    private val chunkStartLines: IntArray
) {
    init {
        require(chunks.sumOf { it.lines.size } == snapshot.lineCount) {
            "Tokenized document must contain exactly one token result per logical line."
        }
    }

    /** Number of tokenized logical lines. */
    val lineCount: Int
        get() = snapshot.lineCount

    /**
     * Returns tokens for one logical line.
     *
     * @param lineIndex Zero-based logical line index.
     * @return Ordered semantic tokens.
     */
    fun tokensForLine(lineIndex: Int): List<EditorToken> = tokenizedLine(lineIndex).tokens

    /**
     * Returns complete lexical information for one logical line.
     *
     * @param lineIndex Zero-based logical line index.
     * @return Tokenization state and semantic tokens for the line.
     */
    fun tokenizedLine(lineIndex: Int): EditorTokenizedLine {
        val location = locate(lineIndex)
        return chunks[location.chunkIndex].lines[location.lineInChunk]
    }

    internal fun splice(
        nextSnapshot: EditorDocumentSnapshot,
        firstChangedLine: Int,
        changedLines: List<EditorTokenizedLine>,
        previousSuffixStart: Int
    ): EditorTokenizedDocument {
        val startLocation = locate(firstChangedLine)
        val retainedPrefix = chunks[startLocation.chunkIndex].lines.take(startLocation.lineInChunk)
        val suffixLocation = if (previousSuffixStart < lineCount) locate(previousSuffixStart) else null
        val retainedSuffix = suffixLocation?.let { chunks[it.chunkIndex].lines.drop(it.lineInChunk) }.orEmpty()
        val rebuiltLines = retainedPrefix + changedLines + retainedSuffix
        val nextChunks = buildList {
            addAll(chunks.take(startLocation.chunkIndex))
            addAll(rebuiltLines.toTokenChunks())
            if (suffixLocation != null) addAll(chunks.drop(suffixLocation.chunkIndex + 1))
        }
        return create(nextSnapshot, language, nextChunks)
    }

    private fun locate(lineIndex: Int): TokenLineLocation {
        require(lineIndex in 0 until lineCount) { "Tokenized line is outside the document." }
        var low = 0
        var high = chunkStartLines.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val start = chunkStartLines[middle]
            val endExclusive = start + chunks[middle].lines.size
            when {
                lineIndex < start -> high = middle - 1
                lineIndex >= endExclusive -> low = middle + 1
                else -> return TokenLineLocation(middle, lineIndex - start)
            }
        }
        error("Unable to resolve tokenized line $lineIndex.")
    }

    private data class TokenLineLocation(val chunkIndex: Int, val lineInChunk: Int)

    companion object {
        internal fun fromLines(
            snapshot: EditorDocumentSnapshot,
            language: CodeLanguage,
            lines: List<EditorTokenizedLine>
        ): EditorTokenizedDocument = create(snapshot, language, lines.toTokenChunks())

        private fun create(
            snapshot: EditorDocumentSnapshot,
            language: CodeLanguage,
            chunks: List<EditorTokenLineChunk>
        ): EditorTokenizedDocument {
            val starts = IntArray(chunks.size)
            var lineOffset = 0
            chunks.forEachIndexed { index, chunk ->
                starts[index] = lineOffset
                lineOffset += chunk.lines.size
            }
            return EditorTokenizedDocument(snapshot, language, chunks, starts)
        }
    }
}

private fun List<EditorTokenizedLine>.toTokenChunks(): List<EditorTokenLineChunk> {
    return chunked(TOKEN_LINE_CHUNK_SIZE).map(::EditorTokenLineChunk)
}

/**
 * Stateless full and incremental document tokenization engine.
 */
object EditorSyntaxEngine {
    /**
     * Produces a current-version token model for the immediate frame after a small edit.
     *
     * Background tokenization remains authoritative. This projection retokenizes only the directly
     * changed lines and temporarily retains the previous suffix, preventing the viewport from
     * dropping semantic colors while asynchronous lexical-state convergence is still running.
     * Oversized changed lines remain temporarily unstyled instead of being tokenized on the UI
     * thread. Large structural, batched, or non-consecutive changes return `null`.
     *
     * @param snapshot Current immutable document snapshot.
     * @param support Active language capabilities.
     * @param previous Previous complete or presentation token model.
     * @param changes Changes that produced [snapshot].
     * @return A structurally aligned presentation model, or `null` when immediate projection is unsafe.
     */
    internal fun projectForPresentation(
        snapshot: EditorDocumentSnapshot,
        support: EditorLanguageSupport,
        previous: EditorTokenizedDocument?,
        changes: List<EditorDocumentChange>
    ): EditorTokenizedDocument? {
        val change = changes.singleOrNull() ?: return null
        if (
            previous == null ||
            previous.language.id != support.language.id ||
            previous.snapshot.version != change.beforeVersion ||
            snapshot.version != change.afterVersion
        ) {
            return null
        }

        val firstChangedLine = change.afterRange.start.line.coerceAtMost(snapshot.lineCount - 1)
        val lastChangedLine = change.afterRange.end.line.coerceAtLeast(firstChangedLine)
        val changedLineCount = lastChangedLine - firstChangedLine + 1
        if (changedLineCount > MAX_IMMEDIATE_PRESENTATION_LINES) return null
        var changedCharacterCount = 0
        for (lineIndex in firstChangedLine..lastChangedLine) {
            val lineLength = snapshot.line(lineIndex).length
            if (changedCharacterCount > MAX_IMMEDIATE_PRESENTATION_CHARACTERS - lineLength) {
                changedCharacterCount = MAX_IMMEDIATE_PRESENTATION_CHARACTERS + 1
                break
            }
            changedCharacterCount += lineLength
        }
        val canRetokenizeImmediately = changedCharacterCount <= MAX_IMMEDIATE_PRESENTATION_CHARACTERS

        val tokenizer = support.tokenizer
        var state = when {
            tokenizer == null -> InitialEditorLexicalState
            firstChangedLine == 0 -> tokenizer.initialState
            else -> previous.tokenizedLine(firstChangedLine - 1).endState
        }
        val changedLines = ArrayList<EditorTokenizedLine>(changedLineCount)
        for (lineIndex in firstChangedLine..lastChangedLine) {
            val tokenizedLine = tokenizer?.takeIf { canRetokenizeImmediately }
                ?.tokenizeLine(snapshot.line(lineIndex), state)
                ?: EditorTokenizedLine(state, state, emptyList())
            changedLines += tokenizedLine
            state = tokenizedLine.endState
        }

        val previousSuffixStart = change.beforeRange.end.line + 1
        return previous.splice(snapshot, firstChangedLine, changedLines, previousSuffixStart)
    }

    /**
     * Tokenizes a snapshot, reusing an unchanged prefix and converged suffix when possible.
     *
     * A single ordinary edit retokenizes from its first affected line until both line text and
     * incoming lexical state converge with [previous]. Batched changes and unrelated previous
     * models use a deterministic full pass. This keeps multiline comments and strings correct
     * without coupling the tokenizer to UI rendering.
     *
     * @param snapshot Current immutable document snapshot.
     * @param support Active language capabilities.
     * @param previous Previous token model, if available.
     * @param changes Ordered changes that produced [snapshot].
     * @param checkpoint Cooperative cancellation checkpoint invoked during bounded work.
     * @return Complete semantic-token model for [snapshot].
     */
    fun tokenize(
        snapshot: EditorDocumentSnapshot,
        support: EditorLanguageSupport,
        previous: EditorTokenizedDocument? = null,
        changes: List<EditorDocumentChange> = emptyList(),
        checkpoint: EditorCancellationCheckpoint = EditorCancellationCheckpoint.None
    ): EditorTokenizedDocument {
        val tokenizer = support.tokenizer
        if (tokenizer == null) {
            val plainLines = List(snapshot.lineCount) { lineIndex ->
                if (lineIndex % TOKEN_LINE_CHUNK_SIZE == 0) checkpoint.ensureActive()
                EditorTokenizedLine(InitialEditorLexicalState, InitialEditorLexicalState, emptyList())
            }
            return EditorTokenizedDocument.fromLines(snapshot, support.language, plainLines)
        }

        val previousIsCompatible = previous != null &&
            previous.language.id == support.language.id &&
            changes.size == 1 &&
            changes.single().beforeVersion == previous.snapshot.version &&
            changes.single().afterVersion == snapshot.version
        return if (previousIsCompatible) {
            tokenizeIncrementally(snapshot, tokenizer, requireNotNull(previous), changes.single(), checkpoint)
        } else {
            tokenizeFully(snapshot, support.language, tokenizer, checkpoint)
        }
    }

    private fun tokenizeFully(
        snapshot: EditorDocumentSnapshot,
        language: CodeLanguage,
        tokenizer: EditorSyntaxTokenizer,
        checkpoint: EditorCancellationCheckpoint
    ): EditorTokenizedDocument {
        val lines = ArrayList<EditorTokenizedLine>(snapshot.lineCount)
        var state = tokenizer.initialState
        for (lineIndex in 0 until snapshot.lineCount) {
            if (lineIndex % TOKEN_LINE_CHUNK_SIZE == 0) checkpoint.ensureActive()
            val tokenizedLine = tokenizer.tokenizeLine(snapshot.line(lineIndex), state)
            lines += tokenizedLine
            state = tokenizedLine.endState
        }
        return EditorTokenizedDocument.fromLines(snapshot, language, lines)
    }

    private fun tokenizeIncrementally(
        snapshot: EditorDocumentSnapshot,
        tokenizer: EditorSyntaxTokenizer,
        previous: EditorTokenizedDocument,
        change: EditorDocumentChange,
        checkpoint: EditorCancellationCheckpoint
    ): EditorTokenizedDocument {
        val firstChangedLine = change.beforeRange.start.line.coerceAtMost(snapshot.lineCount - 1)
        val changedLines = ArrayList<EditorTokenizedLine>()
        var state = if (firstChangedLine == 0) {
            tokenizer.initialState
        } else {
            previous.tokenizedLine(firstChangedLine - 1).endState
        }
        var currentLine = firstChangedLine

        val oldSuffixStart = change.beforeRange.end.line + 1
        val newSuffixStart = change.afterRange.end.line + 1
        val lineDelta = newSuffixStart - oldSuffixStart

        while (currentLine < snapshot.lineCount) {
            if ((currentLine - firstChangedLine) % TOKEN_LINE_CHUNK_SIZE == 0) checkpoint.ensureActive()
            val oldLine = currentLine - lineDelta
            val canReuseSuffix = currentLine >= newSuffixStart &&
                oldLine >= oldSuffixStart &&
                oldLine in 0 until previous.lineCount &&
                snapshot.line(currentLine) == previous.snapshot.line(oldLine) &&
                state == previous.tokenizedLine(oldLine).startState &&
                snapshot.lineCount - currentLine == previous.lineCount - oldLine
            if (canReuseSuffix) {
                return previous.splice(snapshot, firstChangedLine, changedLines, oldLine)
            }

            val tokenizedLine = tokenizer.tokenizeLine(snapshot.line(currentLine), state)
            changedLines += tokenizedLine
            state = tokenizedLine.endState
            currentLine++
        }

        return previous.splice(snapshot, firstChangedLine, changedLines, previous.lineCount)
    }
}
