package com.devuloopers.knet.ui.desktop.codeeditor.document

private const val DEFAULT_DOCUMENT_CHUNK_SIZE = 256

/**
 * Describes the behavioral category of an editor mutation.
 *
 * Undo grouping and language invalidation use this value without inspecting raw keyboard events.
 */
enum class EditorEditKind {
    /** Text was inserted without replacing existing characters. */
    Insertion,

    /** Existing text was removed without inserting replacement characters. */
    Deletion,

    /** Existing text was replaced with different text. */
    Replacement,

    /** A line split, merge, paste, format, or other explicit structural operation occurred. */
    Structural
}

/**
 * Requested replacement of [range] with [replacement].
 *
 * @property range Half-open document range to replace.
 * @property replacement Text inserted at the beginning of [range]. Newlines create logical lines.
 * @property kind Behavioral category used by undo grouping and observers.
 */
data class EditorTextEdit(
    val range: EditorRange,
    val replacement: String,
    val kind: EditorEditKind
)

/**
 * Immutable description of an edit that was accepted by an [EditorDocument].
 *
 * The removed and inserted fragments are retained instead of full-document snapshots, allowing
 * undo/redo memory to scale with changed content rather than total document size.
 *
 * @property beforeVersion Document version before the edit.
 * @property afterVersion Document version after the edit.
 * @property beforeRange Range removed from the pre-edit document.
 * @property afterRange Range occupied by [insertedText] in the post-edit document.
 * @property removedText Text removed from [beforeRange].
 * @property insertedText Text inserted into [afterRange].
 * @property kind Behavioral category supplied with the edit.
 */
data class EditorDocumentChange(
    val beforeVersion: Long,
    val afterVersion: Long,
    val beforeRange: EditorRange,
    val afterRange: EditorRange,
    val removedText: String,
    val insertedText: String,
    val kind: EditorEditKind
)

internal data class EditorLineChunk(val lines: List<String>)

/**
 * Immutable, versioned view of an editor document.
 *
 * Snapshots share unchanged line chunks with later versions. Accessing individual lines therefore
 * remains cheap while consumers serialize the full document only when they explicitly call [text].
 */
class EditorDocumentSnapshot internal constructor(
    internal val chunks: List<EditorLineChunk>,
    private val chunkStartLines: IntArray,
    /** Monotonically increasing document version. */
    val version: Long
) {
    /** Number of logical lines. A document always contains at least one line. */
    val lineCount: Int = chunks.sumOf { it.lines.size }

    /** Number of UTF-16 characters, including logical newline separators. */
    val characterCount: Int by lazy {
        chunks.sumOf { chunk -> chunk.lines.sumOf(String::length) } + (lineCount - 1).coerceAtLeast(0)
    }

    /**
     * Returns a logical line without copying the document.
     *
     * @param index Zero-based line index.
     * @return Line content without a trailing newline.
     * @throws IndexOutOfBoundsException when [index] is outside the document.
     */
    fun line(index: Int): String {
        val location = locate(index)
        return chunks[location.chunkIndex].lines[location.lineInChunk]
    }

    /**
     * Returns an immutable copy of the requested logical line range.
     *
     * @param range Zero-based line indices to copy.
     * @return Ordered line content for [range].
     * @throws IndexOutOfBoundsException when any requested index is outside the document.
     */
    fun lines(range: IntRange = 0 until lineCount): List<String> {
        if (range.isEmpty()) return emptyList()
        require(range.first >= 0 && range.last < lineCount) { "Requested line range is outside the document." }
        return buildList(range.count()) {
            for (lineIndex in range) add(line(lineIndex))
        }
    }

    /**
     * Serializes the complete snapshot using `\n` separators.
     *
     * This is intentionally explicit because serialization is O(document size). Rendering and
     * language services should prefer [line] and [lines].
     *
     * @return Complete document text.
     */
    fun text(): String = buildString(characterCount) {
        var currentLine = 0
        for (chunk in chunks) {
            for (line in chunk.lines) {
                if (currentLine > 0) append('\n')
                append(line)
                currentLine++
            }
        }
    }

    /**
     * Extracts a half-open range without serializing unrelated document content.
     *
     * @param range Range expressed in this snapshot's coordinates. Positions are clamped.
     * @return Exact selected text using `\n` between logical lines.
     */
    fun text(range: EditorRange): String {
        val start = clamp(range.start)
        val end = clamp(range.end)
        if (start == end) return ""
        if (start.line == end.line) return line(start.line).substring(start.column, end.column)
        return buildString {
            append(line(start.line).substring(start.column))
            for (lineIndex in (start.line + 1) until end.line) {
                append('\n')
                append(line(lineIndex))
            }
            append('\n')
            append(line(end.line).substring(0, end.column))
        }
    }

    /**
     * Clamps a potentially stale position to this snapshot.
     *
     * @param position Position produced against this or an earlier document version.
     * @return Valid position inside this snapshot.
     */
    fun clamp(position: EditorPosition): EditorPosition {
        val safeLine = position.line.coerceIn(0, lineCount - 1)
        return EditorPosition(safeLine, position.column.coerceIn(0, line(safeLine).length))
    }

    internal fun locate(lineIndex: Int): LineLocation {
        if (lineIndex !in 0 until lineCount) {
            throw IndexOutOfBoundsException("Line $lineIndex is outside 0 until $lineCount.")
        }
        var low = 0
        var high = chunkStartLines.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val start = chunkStartLines[middle]
            val endExclusive = start + chunks[middle].lines.size
            when {
                lineIndex < start -> high = middle - 1
                lineIndex >= endExclusive -> low = middle + 1
                else -> return LineLocation(middle, lineIndex - start)
            }
        }
        error("Unable to resolve line $lineIndex.")
    }

    internal data class LineLocation(val chunkIndex: Int, val lineInChunk: Int)
}

/**
 * Mutable document boundary used by editor sessions.
 *
 * Implementations must return immutable snapshots and monotonic versions. Mutations are expected
 * to be confined to one editor session thread; snapshots may be read from worker coroutines.
 */
interface EditorDocument {
    /** Latest immutable document snapshot. */
    val snapshot: EditorDocumentSnapshot

    /**
     * Applies one range replacement.
     *
     * @param edit Replacement to apply. Positions are clamped to the latest snapshot.
     * @return Exact accepted change, including removed text for delta-based undo.
     */
    fun apply(edit: EditorTextEdit): EditorDocumentChange

    /**
     * Replaces all content and starts a new document version.
     *
     * @param text New complete document content.
     * @return Exact accepted replacement change.
     */
    fun replaceAll(text: String): EditorDocumentChange
}

/**
 * Chunked line-oriented [EditorDocument] optimized for network payloads and source snippets.
 *
 * A single-line change copies at most one bounded line chunk plus the outer chunk index instead of
 * copying every line. The public [EditorDocument] boundary permits a future rope or piece-tree
 * implementation without changing editor sessions, language services, rendering, or consumers.
 *
 * @param initialText Initial document content.
 * @param chunkSize Maximum target number of lines stored in one immutable chunk.
 */
class ChunkedEditorDocument(
    initialText: String = "",
    private val chunkSize: Int = DEFAULT_DOCUMENT_CHUNK_SIZE
) : EditorDocument {
    init {
        require(chunkSize > 0) { "Document chunk size must be positive." }
    }

    private var currentSnapshot = createSnapshot(splitLines(initialText), version = 0L)

    override val snapshot: EditorDocumentSnapshot
        get() = currentSnapshot

    override fun apply(edit: EditorTextEdit): EditorDocumentChange {
        val before = currentSnapshot
        val normalizedRange = normalizeRange(before, edit.range)
        val removedText = before.text(normalizedRange)
        val replacementLines = splitLines(edit.replacement)
        if (removedText == edit.replacement) {
            val unchangedRange = EditorRange(
                start = normalizedRange.start,
                end = insertedEnd(normalizedRange.start, replacementLines)
            )
            return EditorDocumentChange(
                beforeVersion = before.version,
                afterVersion = before.version,
                beforeRange = normalizedRange,
                afterRange = unchangedRange,
                removedText = removedText,
                insertedText = edit.replacement,
                kind = edit.kind
            )
        }
        val replacementDocumentLines = buildReplacementLines(before, normalizedRange, replacementLines)
        val startLocation = before.locate(normalizedRange.start.line)
        val endLocation = before.locate(normalizedRange.end.line)

        val retainedPrefix = before.chunks[startLocation.chunkIndex].lines.take(startLocation.lineInChunk)
        val retainedSuffix = before.chunks[endLocation.chunkIndex].lines.drop(endLocation.lineInChunk + 1)
        val rebuiltSection = retainedPrefix + replacementDocumentLines + retainedSuffix

        val nextChunks = buildList {
            addAll(before.chunks.take(startLocation.chunkIndex))
            addAll(toChunks(rebuiltSection))
            addAll(before.chunks.drop(endLocation.chunkIndex + 1))
        }
        currentSnapshot = createSnapshotFromChunks(nextChunks, before.version + 1)

        val afterRange = EditorRange(
            start = normalizedRange.start,
            end = insertedEnd(normalizedRange.start, replacementLines)
        )
        return EditorDocumentChange(
            beforeVersion = before.version,
            afterVersion = currentSnapshot.version,
            beforeRange = normalizedRange,
            afterRange = afterRange,
            removedText = removedText,
            insertedText = edit.replacement,
            kind = edit.kind
        )
    }

    override fun replaceAll(text: String): EditorDocumentChange {
        val before = currentSnapshot
        val lastLineIndex = before.lineCount - 1
        val fullRange = EditorRange(
            start = EditorPosition(0, 0),
            end = EditorPosition(lastLineIndex, before.line(lastLineIndex).length)
        )
        return apply(EditorTextEdit(fullRange, text, EditorEditKind.Structural))
    }

    private fun normalizeRange(snapshot: EditorDocumentSnapshot, range: EditorRange): EditorRange {
        val start = snapshot.clamp(range.start)
        val end = snapshot.clamp(range.end)
        return if (start <= end) EditorRange(start, end) else EditorRange(end, start)
    }

    private fun buildReplacementLines(
        snapshot: EditorDocumentSnapshot,
        range: EditorRange,
        replacementLines: List<String>
    ): List<String> {
        val prefix = snapshot.line(range.start.line).substring(0, range.start.column)
        val suffix = snapshot.line(range.end.line).substring(range.end.column)
        return if (replacementLines.size == 1) {
            listOf(prefix + replacementLines.single() + suffix)
        } else {
            buildList(replacementLines.size) {
                add(prefix + replacementLines.first())
                addAll(replacementLines.subList(1, replacementLines.lastIndex))
                add(replacementLines.last() + suffix)
            }
        }
    }

    private fun insertedEnd(start: EditorPosition, replacementLines: List<String>): EditorPosition {
        return if (replacementLines.size == 1) {
            EditorPosition(start.line, start.column + replacementLines.single().length)
        } else {
            EditorPosition(start.line + replacementLines.lastIndex, replacementLines.last().length)
        }
    }

    private fun createSnapshot(lines: List<String>, version: Long): EditorDocumentSnapshot {
        return createSnapshotFromChunks(toChunks(lines), version)
    }

    private fun createSnapshotFromChunks(chunks: List<EditorLineChunk>, version: Long): EditorDocumentSnapshot {
        val safeChunks = if (chunks.isEmpty()) listOf(EditorLineChunk(listOf(""))) else chunks
        val starts = IntArray(safeChunks.size)
        var lineOffset = 0
        safeChunks.forEachIndexed { index, chunk ->
            starts[index] = lineOffset
            lineOffset += chunk.lines.size
        }
        return EditorDocumentSnapshot(safeChunks, starts, version)
    }

    private fun toChunks(lines: List<String>): List<EditorLineChunk> {
        val safeLines = lines.ifEmpty { listOf("") }
        return safeLines.chunked(chunkSize).map(::EditorLineChunk)
    }

    private fun splitLines(text: String): List<String> {
        val result = mutableListOf<String>()
        var lineStart = 0
        for (index in text.indices) {
            if (text[index] == '\n') {
                val lineEnd = if (index > lineStart && text[index - 1] == '\r') index - 1 else index
                result += text.substring(lineStart, lineEnd)
                lineStart = index + 1
            }
        }
        result += text.substring(lineStart).removeSuffix("\r")
        return result
    }
}
