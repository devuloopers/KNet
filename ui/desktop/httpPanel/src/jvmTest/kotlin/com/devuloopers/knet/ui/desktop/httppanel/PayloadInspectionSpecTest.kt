package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests verifying [PayloadInspectionSpec.fromPayload] format resolution,
 * [CodeLanguage] mapping, and formattedText properties.
 */
class PayloadInspectionSpecTest {

    @Test
    fun testEmptyPayloadReturnsNullResolvedFormatAndPlainLanguage() {
        val emptySpec = PayloadInspectionSpec.fromPayload(emptyList(), "")
        assertTrue(emptySpec.isEmpty)
        assertNull(emptySpec.resolvedFormat)
        assertEquals(CodeLanguage.PLAIN, emptySpec.codeLanguage)
        assertEquals("", emptySpec.formattedText)

        val blankSpec = PayloadInspectionSpec.fromPayload(emptyMap(), "   \n  \t ")
        assertTrue(blankSpec.isEmpty)
        assertNull(blankSpec.resolvedFormat)
        assertEquals(CodeLanguage.PLAIN, blankSpec.codeLanguage)
    }

    @Test
    fun testJsonPayloadResolvesJsonFormatAndJsonLanguage() {
        val headers = mapOf("content-type" to "application/json")
        val jsonText = """{"status":"active","count":100}"""

        val spec = PayloadInspectionSpec.fromPayload(headers, jsonText)

        assertFalse(spec.isEmpty)
        assertIs<BodyFormat.Json>(spec.resolvedFormat)
        assertEquals(CodeLanguage.JSON, spec.codeLanguage)
        assertTrue(spec.formattedText.contains("status"))
    }

    @Test
    fun testGraphQlJsonPayloadResolvesGraphQlFormatAndGraphQlLanguage() {
        val headers = mapOf("content-type" to "application/json")
        val gqlPayload = """
            {
              "query": "query GetSymbols { symbols { id name } }",
              "operationName": "GetSymbols"
            }
        """.trimIndent()

        val spec = PayloadInspectionSpec.fromPayload(headers, gqlPayload)

        assertFalse(spec.isEmpty)
        assertIs<BodyFormat.GraphQL>(spec.resolvedFormat)
        assertEquals(CodeLanguage.GRAPHQL, spec.codeLanguage)
        assertTrue(spec.formattedText.contains("GetSymbols"))
    }

    @Test
    fun testXmlPayloadResolvesXmlFormatAndXmlLanguage() {
        val headers = mapOf("content-type" to "application/xml")
        val xmlText = "<response><item id=\"1\"/></response>"

        val spec = PayloadInspectionSpec.fromPayload(headers, xmlText)

        assertFalse(spec.isEmpty)
        assertIs<BodyFormat.Xml>(spec.resolvedFormat)
        assertEquals(CodeLanguage.XML, spec.codeLanguage)
        assertTrue(spec.formattedText.contains("item"))
    }

    @Test
    fun testHtmlPayloadResolvesHtmlFormatAndHtmlLanguage() {
        val headers = mapOf("content-type" to "text/html")
        val htmlText = "<!DOCTYPE html><html><body><h1>Title</h1></body></html>"

        val spec = PayloadInspectionSpec.fromPayload(headers, htmlText)

        assertFalse(spec.isEmpty)
        assertIs<BodyFormat.Html>(spec.resolvedFormat)
        assertEquals(CodeLanguage.HTML, spec.codeLanguage)
        assertTrue(spec.formattedText.contains("Title"))
    }

    @Test
    fun testJavaScriptPayloadResolvesJsFormatAndJavaScriptLanguage() {
        val headers = mapOf("content-type" to "text/javascript")
        val jsText = "function test() { console.log('hello'); }"

        val spec = PayloadInspectionSpec.fromPayload(headers, jsText)

        assertFalse(spec.isEmpty)
        assertIs<BodyFormat.Js>(spec.resolvedFormat)
        assertEquals(CodeLanguage.JAVASCRIPT, spec.codeLanguage)
        assertTrue(spec.formattedText.contains("console.log"))
    }

    @Test
    fun testPlainTextPayloadResolvesRawTextAndPlainLanguage() {
        val headers = mapOf("content-type" to "text/plain")
        val text = "Simple raw string"

        val spec = PayloadInspectionSpec.fromPayload(headers, text)

        assertFalse(spec.isEmpty)
        assertIs<BodyFormat.RawText>(spec.resolvedFormat)
        assertEquals(CodeLanguage.PLAIN, spec.codeLanguage)
        assertEquals(text, spec.formattedText)
    }

    @Test
    fun testNdJsonPayloadFormatsRecordsThroughTheExistingJsonLanguage() {
        val ndJson = "{\"id\":1,\"active\":true}\n{\"id\":2,\"active\":false}"

        val spec = PayloadInspectionSpec.fromPayload(
            mapOf("content-type" to "application/x-ndjson"),
            ndJson
        )

        val format = assertIs<BodyFormat.JsonStream>(spec.resolvedFormat)
        assertEquals(2, format.frames.size)
        assertEquals(CodeLanguage.JSON, spec.codeLanguage)
        assertTrue(spec.formattedText.contains("\n\n"))
        assertTrue(spec.formattedText.lines().size > 4)
    }
}
