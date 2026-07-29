package com.devuloopers.knet

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.editor.highlighter.CodeHighlighterRegistry
import com.devuloopers.knet.editor.highlighter.HtmlLanguageHighlighter
import com.devuloopers.knet.editor.highlighter.JsonLanguageHighlighter
import com.devuloopers.knet.editor.highlighter.PlainTextLanguageHighlighter
import com.devuloopers.knet.editor.highlighter.XmlLanguageHighlighter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeHighlighterTest {

    @Test
    fun testCodeHighlighterRegistryResolution() {
        val jsonStrategy = CodeHighlighterRegistry.resolve(BodyFormat.Json("{}"))
        assertTrue(jsonStrategy is JsonLanguageHighlighter, "JSON format must resolve JsonLanguageHighlighter")

        val htmlStrategy = CodeHighlighterRegistry.resolve(BodyFormat.Html("<html></html>"))
        assertTrue(htmlStrategy is HtmlLanguageHighlighter, "HTML format must resolve HtmlLanguageHighlighter")

        val xmlStrategy = CodeHighlighterRegistry.resolve(BodyFormat.Xml("<xml></xml>"))
        assertTrue(xmlStrategy is XmlLanguageHighlighter, "XML format must resolve XmlLanguageHighlighter")

        val jsStrategy = CodeHighlighterRegistry.resolve(BodyFormat.Js("console.log('test')"))
        assertTrue(jsStrategy is com.devuloopers.knet.editor.highlighter.JsLanguageHighlighter, "JS format must resolve JsLanguageHighlighter")

        val cssStrategy = CodeHighlighterRegistry.resolve(BodyFormat.Css("body { color: red; }"))
        assertTrue(cssStrategy is com.devuloopers.knet.editor.highlighter.CssLanguageHighlighter, "CSS format must resolve CssLanguageHighlighter")

        val grpcWebStrategy = CodeHighlighterRegistry.resolve(BodyFormat.GrpcWeb(emptyList()))
        assertTrue(grpcWebStrategy is JsonLanguageHighlighter, "gRPC-Web format must resolve JsonLanguageHighlighter")

        val plainStrategy = CodeHighlighterRegistry.resolve(BodyFormat.RawText("plain text"))
        assertTrue(plainStrategy is PlainTextLanguageHighlighter, "RawText format must resolve PlainTextLanguageHighlighter")
    }

    @Test
    fun testJsonLanguageHighlighterFolding() {
        val strategy = JsonLanguageHighlighter()
        val lines = listOf(
            "{",
            "  \"key\": \"value\"",
            "}"
        )
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
