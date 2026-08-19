package com.devuloopers.knet.ui.desktop.codeeditor.language

import com.devuloopers.knet.ui.desktop.codeeditor.document.ChunkedEditorDocument
import com.devuloopers.knet.ui.desktop.codeeditor.concurrency.EditorCancellationCheckpoint
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorEditKind
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorTextEdit
import com.devuloopers.knet.ui.desktop.codeeditor.language.builtin.BuiltInEditorLanguages
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditorSyntaxEngineTest {
    @Test
    fun multilineCommentStatePropagatesAcrossLines() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JAVASCRIPT)
        val snapshot = ChunkedEditorDocument("const a = 1; /* start\nstill comment\nend */ const b = 2;").snapshot

        val result = EditorSyntaxEngine.tokenize(snapshot, support)

        assertTrue(result.tokensForLine(1).any { it.category == EditorTokenCategory.Standard.Comment })
        assertTrue(result.tokensForLine(2).first().category == EditorTokenCategory.Standard.Comment)
        assertTrue(result.tokensForLine(2).any { it.category == EditorTokenCategory.Standard.Keyword })
    }

    @Test
    fun incrementalTokenizationReusesConvergedSuffix() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JAVASCRIPT)
        val document = ChunkedEditorDocument("const a = 1;\nconst b = 2;\nconst c = 3;")
        val before = EditorSyntaxEngine.tokenize(document.snapshot, support)
        val change = document.apply(
            EditorTextEdit(
                EditorRange(EditorPosition(0, 10), EditorPosition(0, 11)),
                "9",
                EditorEditKind.Replacement
            )
        )

        val after = EditorSyntaxEngine.tokenize(document.snapshot, support, before, listOf(change))

        assertEquals(3, after.lineCount)
        assertSame(before.tokenizedLine(1), after.tokenizedLine(1))
        assertSame(before.tokenizedLine(2), after.tokenizedLine(2))
    }

    @Test
    fun incrementalTokenizationReusesUnaffectedChunksInLargeDocument() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JAVASCRIPT)
        val document = ChunkedEditorDocument((0 until 10_000).joinToString("\n") { "const value$it = $it;" })
        val before = EditorSyntaxEngine.tokenize(document.snapshot, support)
        val change = document.apply(
            EditorTextEdit(
                EditorRange(EditorPosition(5_000, 6), EditorPosition(5_000, 11)),
                "item",
                EditorEditKind.Replacement
            )
        )

        val after = EditorSyntaxEngine.tokenize(document.snapshot, support, before, listOf(change))

        assertEquals(before.chunks.size, after.chunks.size)
        assertEquals(1, before.chunks.indices.count { before.chunks[it] !== after.chunks[it] })
        assertSame(before.chunks.first(), after.chunks.first())
        assertSame(before.chunks.last(), after.chunks.last())
    }

    @Test
    fun immediatePresentationRetokenizesEditedLineWithoutDroppingUnchangedTokens() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JAVASCRIPT)
        val document = ChunkedEditorDocument("const first = 1;\nconst second = 2;\nconst third = 3;")
        val before = EditorSyntaxEngine.tokenize(document.snapshot, support)
        val change = document.apply(
            EditorTextEdit(
                EditorRange(EditorPosition(1, 15), EditorPosition(1, 16)),
                "\"two\"",
                EditorEditKind.Replacement
            )
        )

        val presentation = requireNotNull(
            EditorSyntaxEngine.projectForPresentation(
                snapshot = document.snapshot,
                support = support,
                previous = before,
                changes = listOf(change)
            )
        )

        assertSame(document.snapshot, presentation.snapshot)
        assertSame(before.tokenizedLine(0), presentation.tokenizedLine(0))
        assertSame(before.tokenizedLine(2), presentation.tokenizedLine(2))
        assertTrue(
            presentation.tokensForLine(1).any { token ->
                token.category == EditorTokenCategory.Standard.String
            }
        )
    }

    @Test
    fun immediatePresentationKeepsSuffixAlignedAfterLineSplit() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JAVASCRIPT)
        val document = ChunkedEditorDocument("const first = 1;\nconst second = 2;\nconst third = 3;")
        val before = EditorSyntaxEngine.tokenize(document.snapshot, support)
        val change = document.apply(
            EditorTextEdit(
                EditorRange.caret(EditorPosition(1, 6)),
                "\n",
                EditorEditKind.Structural
            )
        )

        val presentation = requireNotNull(
            EditorSyntaxEngine.projectForPresentation(
                snapshot = document.snapshot,
                support = support,
                previous = before,
                changes = listOf(change)
            )
        )

        assertEquals(document.snapshot.lineCount, presentation.lineCount)
        assertSame(before.tokenizedLine(2), presentation.tokenizedLine(3))
    }

    @Test
    fun immediatePresentationDoesNotTokenizeAnOversizedChangedLineOnTheUiPath() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JAVASCRIPT)
        val oversizedLine = "const value = \"${"x".repeat(40_000)}\";"
        val document = ChunkedEditorDocument("$oversizedLine\nconst retained = true;")
        val before = EditorSyntaxEngine.tokenize(document.snapshot, support)
        val change = document.apply(
            EditorTextEdit(
                EditorRange(EditorPosition(0, 0), EditorPosition(0, 1)),
                "l",
                EditorEditKind.Replacement
            )
        )

        val presentation = requireNotNull(
            EditorSyntaxEngine.projectForPresentation(
                snapshot = document.snapshot,
                support = support,
                previous = before,
                changes = listOf(change)
            )
        )

        assertTrue(presentation.tokensForLine(0).isEmpty())
        assertSame(before.tokenizedLine(1), presentation.tokenizedLine(1))
    }

    @Test
    fun immediatePresentationChainsAcrossRapidEditsBeforeBackgroundConvergence() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JAVASCRIPT)
        val document = ChunkedEditorDocument("const value = 1;")
        val completed = EditorSyntaxEngine.tokenize(document.snapshot, support)
        val firstChange = document.apply(
            EditorTextEdit(
                EditorRange(EditorPosition(0, 14), EditorPosition(0, 15)),
                "2",
                EditorEditKind.Replacement
            )
        )
        val firstPresentation = requireNotNull(
            EditorSyntaxEngine.projectForPresentation(
                snapshot = document.snapshot,
                support = support,
                previous = completed,
                changes = listOf(firstChange)
            )
        )
        val secondChange = document.apply(
            EditorTextEdit(
                EditorRange(EditorPosition(0, 14), EditorPosition(0, 15)),
                "3",
                EditorEditKind.Replacement
            )
        )

        val secondPresentation = requireNotNull(
            EditorSyntaxEngine.projectForPresentation(
                snapshot = document.snapshot,
                support = support,
                previous = firstPresentation,
                changes = listOf(secondChange)
            )
        )

        assertSame(document.snapshot, secondPresentation.snapshot)
        assertTrue(
            secondPresentation.tokensForLine(0).any { token ->
                token.category == EditorTokenCategory.Standard.Number
            }
        )
    }

    @Test
    fun jsonFoldingIgnoresBracesInsideStrings() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.JSON)
        val snapshot = ChunkedEditorDocument(
            "{\n  \"text\": \"not a } fold\",\n  \"nested\": {\n    \"value\": true\n  }\n}"
        ).snapshot

        val folds = requireNotNull(support.foldingProvider).calculate(
            snapshot,
            EditorCancellationCheckpoint.None
        )

        assertEquals(listOf(0 to 5, 2 to 4), folds.map { it.startLine to it.endLine })
    }

    @Test
    fun markupTokenizerProducesTagAttributeAndStringTokens() {
        val support = BuiltInEditorLanguages.registry.resolve(CodeLanguage.HTML)
        val snapshot = ChunkedEditorDocument("<section id=\"main\">value</section>").snapshot

        val result = EditorSyntaxEngine.tokenize(snapshot, support)
        val categories = result.tokensForLine(0).map(EditorToken::category)

        assertTrue(EditorTokenCategory.Standard.Tag in categories)
        assertTrue(EditorTokenCategory.Standard.Attribute in categories)
        assertTrue(EditorTokenCategory.Standard.String in categories)
    }
}
