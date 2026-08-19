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
