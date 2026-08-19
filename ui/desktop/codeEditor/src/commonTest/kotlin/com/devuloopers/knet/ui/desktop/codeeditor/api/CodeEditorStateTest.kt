package com.devuloopers.knet.ui.desktop.codeeditor.api

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchEngine
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchOptions
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodeEditorStateTest {
    @Test
    fun searchNavigationUsesSessionSelectionAndReplacementHistory() {
        val state = CodeEditorState(EditorSession("one value\ntwo value"))
        val options = EditorSearchOptions("value")

        state.openSearch(options)
        state.updateSearchResult(EditorSearchEngine.search(state.snapshot, options))

        assertTrue(state.isSearchVisible)
        assertEquals(EditorPosition(0, 4), state.selection?.range?.start)
        state.nextSearchMatch()
        assertEquals(EditorPosition(1, 4), state.selection?.range?.start)

        assertTrue(state.replaceActiveSearchMatch("item"))
        assertEquals("one value\ntwo item", state.text())
        assertTrue(state.session.undo())
        assertEquals("one value\ntwo value", state.text())

        state.closeSearch()
        assertFalse(state.isSearchVisible)
        state.close()
    }
}
