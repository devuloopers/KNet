package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.syntax.registry.CodeHighlighterRegistry
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.HtmlLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.JsonLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.PlainTextLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.XmlLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.JsLanguageHighlighter
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.CssLanguageHighlighter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [CodeHighlighterRegistry] strategy resolution and per-language fold range logic.
 *
 * These tests live in `:ui:desktop:codeEditor`'s own test source set so that
 * `internal` declarations are accessible without relaxing module visibility.
 */
class CodeHighlighterTest {

    @Test
    fun testCodeHighlighterRegistryResolution() {
        val jsonStrategy = CodeHighlighterRegistry.resolveByLanguage("json")
        assertTrue(jsonStrategy is JsonLanguageHighlighter, "JSON format must resolve JsonLanguageHighlighter")

        val htmlStrategy = CodeHighlighterRegistry.resolveByLanguage("html")
        assertTrue(htmlStrategy is HtmlLanguageHighlighter, "HTML format must resolve HtmlLanguageHighlighter")

        val xmlStrategy = CodeHighlighterRegistry.resolveByLanguage("xml")
        assertTrue(xmlStrategy is XmlLanguageHighlighter, "XML format must resolve XmlLanguageHighlighter")

        val jsStrategy = CodeHighlighterRegistry.resolveByLanguage("js")
        assertTrue(jsStrategy is JsLanguageHighlighter, "JS format must resolve JsLanguageHighlighter")

        val cssStrategy = CodeHighlighterRegistry.resolveByLanguage("css")
        assertTrue(cssStrategy is CssLanguageHighlighter, "CSS format must resolve CssLanguageHighlighter")

        val plainStrategy = CodeHighlighterRegistry.resolveByLanguage("raw")
        assertTrue(plainStrategy is PlainTextLanguageHighlighter, "Unknown format must resolve PlainTextLanguageHighlighter")
    }

    @Test
    fun testNullAndBlankLanguageIdResolvesToPlainText() {
        assertTrue(CodeHighlighterRegistry.resolveByLanguage(null) is PlainTextLanguageHighlighter)
        assertTrue(CodeHighlighterRegistry.resolveByLanguage("") is PlainTextLanguageHighlighter)
        assertTrue(CodeHighlighterRegistry.resolveByLanguage("   ") is PlainTextLanguageHighlighter)
    }

    @Test
    fun testJsonLanguageHighlighterFolding() {
        val strategy = JsonLanguageHighlighter()
        val lines = listOf("{", "  \"key\": \"value\"", "}")
        val foldRanges = strategy.calculateFoldRanges(lines)
        assertEquals(1, foldRanges.size)
        assertEquals(2, foldRanges[0])
    }

    @Test
    fun testHtmlLanguageHighlighterTagFolding() {
        val strategy = HtmlLanguageHighlighter()
        val lines = listOf(
            "<HTML>",
            "  <HEAD>",
            "    <meta charset=\"utf-8\">",
            "  </HEAD>",
            "  <BODY>",
            "    <H1>Title</H1>",
            "  </BODY>",
            "</HTML>"
        )
        val foldRanges = strategy.calculateFoldRanges(lines)
        assertEquals(3, foldRanges.size)
        assertEquals(7, foldRanges[0], "Root <HTML> tag must fold to line 7 </HTML>")
        assertEquals(3, foldRanges[1], "<HEAD> tag must fold to line 3 </HEAD>")
        assertEquals(6, foldRanges[4], "<BODY> tag must fold to line 6 </BODY>")
    }
}
