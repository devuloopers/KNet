package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ScriptSnippet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying SnippetLibrary item attributes.
 */
class SnippetLibraryTest {

    @Test
    fun `ScriptSnippet values match constructor fields`() {
        val snippet = ScriptSnippet(
            id = "header_check",
            title = "Check header",
            description = "Assert header value",
            codeJs = "pm.response.headers.get('Content-Type')",
            codeKotlin = "response.headers['Content-Type']"
        )
        assertEquals("header_check", snippet.id)
        assertEquals("Check header", snippet.title)
        assertEquals("pm.response.headers.get('Content-Type')", snippet.codeJs)
    }
}
