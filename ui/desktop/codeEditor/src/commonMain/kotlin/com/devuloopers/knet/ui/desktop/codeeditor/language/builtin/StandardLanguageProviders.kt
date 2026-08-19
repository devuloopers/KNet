package com.devuloopers.knet.ui.desktop.codeeditor.language.builtin

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.concurrency.EditorCancellationCheckpoint
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorBracketPair
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorBracketProvider
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorFoldingProvider
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorIndentationProvider
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorSyntaxTokenizer
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenCategory

internal class StandardBracketProvider(
    override val pairs: Set<EditorBracketPair>
) : EditorBracketProvider

internal class BracketIndentationProvider(
    private val openingBrackets: Set<Char>,
    private val closingBrackets: Set<Char>,
    private val indentationUnit: String = "  "
) : EditorIndentationProvider {
    override fun indentationForNewLine(snapshot: EditorDocumentSnapshot, position: EditorPosition): String {
        val safePosition = snapshot.clamp(position)
        val line = snapshot.line(safePosition.line)
        val beforeCaret = line.substring(0, safePosition.column)
        val afterCaret = line.substring(safePosition.column)
        val currentIndentation = line.takeWhile(Char::isWhitespace)
        val addsLevel = beforeCaret.trimEnd().lastOrNull() in openingBrackets
        val closesImmediately = afterCaret.trimStart().firstOrNull() in closingBrackets
        return when {
            addsLevel && !closesImmediately -> currentIndentation + indentationUnit
            else -> currentIndentation
        }
    }
}

internal class TokenAwareBracketFoldingProvider(
    private val tokenizer: EditorSyntaxTokenizer,
    private val bracketPairs: Set<EditorBracketPair>
) : EditorFoldingProvider {
    override fun calculate(
        snapshot: EditorDocumentSnapshot,
        checkpoint: EditorCancellationCheckpoint
    ): List<FoldRegion> {
        val closingByOpening = bracketPairs.associate { it.opening to it.closing }
        val openingByClosing = bracketPairs.associate { it.closing to it.opening }
        val stack = ArrayDeque<Pair<Char, Int>>()
        val folds = mutableListOf<FoldRegion>()
        var lexicalState = tokenizer.initialState

        for (lineIndex in 0 until snapshot.lineCount) {
            if (lineIndex % 256 == 0) checkpoint.ensureActive()
            val line = snapshot.line(lineIndex)
            val tokenized = tokenizer.tokenizeLine(line, lexicalState)
            lexicalState = tokenized.endState
            val ignoredRanges = tokenized.tokens
                .filter { token ->
                    token.category == EditorTokenCategory.Standard.String ||
                        token.category == EditorTokenCategory.Standard.Comment ||
                        token.category == EditorTokenCategory.Standard.Property
                }
                .map { it.startOffset until it.endOffset }
            var ignoredIndex = 0
            for (offset in line.indices) {
                while (ignoredIndex < ignoredRanges.size && offset > ignoredRanges[ignoredIndex].last) ignoredIndex++
                if (ignoredIndex < ignoredRanges.size && offset in ignoredRanges[ignoredIndex]) continue
                val character = line[offset]
                when {
                    character in closingByOpening -> stack.addLast(character to lineIndex)
                    character in openingByClosing -> {
                        val expectedOpening = openingByClosing.getValue(character)
                        val top = stack.lastOrNull()
                        if (top?.first == expectedOpening) {
                            stack.removeLast()
                            if (lineIndex > top.second) {
                                folds += FoldRegion(top.second, lineIndex, character.toString())
                            }
                        }
                    }
                }
            }
        }
        return folds.sortedWith(compareBy(FoldRegion::startLine, FoldRegion::endLine))
    }
}

internal class MarkupFoldingProvider(
    private val voidTags: Set<String> = emptySet()
) : EditorFoldingProvider {
    private val tagPattern = Regex("<\\s*(/?)\\s*([A-Za-z][A-Za-z0-9:._-]*)([^>]*)>")

    override fun calculate(
        snapshot: EditorDocumentSnapshot,
        checkpoint: EditorCancellationCheckpoint
    ): List<FoldRegion> {
        val stack = ArrayDeque<Pair<String, Int>>()
        val folds = mutableListOf<FoldRegion>()
        var inComment = false
        for (lineIndex in 0 until snapshot.lineCount) {
            if (lineIndex % 256 == 0) checkpoint.ensureActive()
            var line = snapshot.line(lineIndex)
            if (inComment) {
                val commentEnd = line.indexOf("-->")
                if (commentEnd < 0) continue
                line = line.substring(commentEnd + 3)
                inComment = false
            }
            while (true) {
                val commentStart = line.indexOf("<!--")
                if (commentStart < 0) break
                val commentEnd = line.indexOf("-->", commentStart + 4)
                line = if (commentEnd < 0) {
                    inComment = true
                    line.substring(0, commentStart)
                } else {
                    line.removeRange(commentStart, commentEnd + 3)
                }
                if (inComment) break
            }

            tagPattern.findAll(line).forEach { match ->
                val closing = match.groupValues[1].isNotEmpty()
                val name = match.groupValues[2].lowercase()
                val tail = match.groupValues[3]
                val selfClosing = tail.trimEnd().endsWith('/') || name in voidTags
                when {
                    selfClosing -> Unit
                    closing -> {
                        val top = stack.lastOrNull()
                        if (top?.first == name) {
                            stack.removeLast()
                            if (lineIndex > top.second) {
                                folds += FoldRegion(top.second, lineIndex, "</$name>")
                            }
                        }
                    }
                    else -> stack.addLast(name to lineIndex)
                }
            }
        }
        return folds.sortedWith(compareBy(FoldRegion::startLine, FoldRegion::endLine))
    }
}
