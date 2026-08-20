package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionTextInputBridgeTest {
    @Test
    fun committedUnicodeAndMultilineTextIsForwardedWithoutTransformation() {
        assertEquals("λ", committedSelectionInput(TextFieldValue("λ")))
        assertEquals("first\nsecond", committedSelectionInput(TextFieldValue("first\nsecond")))
    }

    @Test
    fun activeImeCompositionIsRetainedUntilCommit() {
        assertNull(
            committedSelectionInput(
                TextFieldValue(
                    text = "に",
                    selection = TextRange(1),
                    composition = TextRange(0, 1)
                )
            )
        )
    }

    @Test
    fun emptyInputDoesNotCreateAnEditorMutation() {
        assertNull(committedSelectionInput(TextFieldValue()))
    }
}
