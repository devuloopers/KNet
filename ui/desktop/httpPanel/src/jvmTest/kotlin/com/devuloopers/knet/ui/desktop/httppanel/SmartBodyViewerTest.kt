package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.toCodeLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests verifying strongly-typed format resolution and spec behavior
 * for [com.devuloopers.knet.ui.desktop.httppanel.components.SmartBodyViewer].
 */
class SmartBodyViewerTest {

    @Test
    fun testPayloadInspectionSpecEmptyBehavior() {
        val emptySpec = PayloadInspectionSpec()
        assertTrue(emptySpec.isEmpty)

        val preparingSpec = PayloadInspectionSpec(isPreparing = true)
        assertFalse(preparingSpec.isEmpty)

        val payloadSpec = PayloadInspectionSpec(rawBody = "{\"key\":\"value\"}")
        assertFalse(payloadSpec.isEmpty)
    }

    @Test
    fun testCodeLanguageResolutionFromId() {
        assertEquals(CodeLanguage.JSON, CodeLanguage.fromId("json"))
        assertEquals(CodeLanguage.JSON, CodeLanguage.fromId("JSON"))
        assertEquals(CodeLanguage.GRAPHQL, CodeLanguage.fromId("graphql"))
        assertEquals(CodeLanguage.GRAPHQL, CodeLanguage.fromId("gql"))
        assertEquals(CodeLanguage.XML, CodeLanguage.fromId("xml"))
        assertEquals(CodeLanguage.HTML, CodeLanguage.fromId("html"))
        assertEquals(CodeLanguage.JAVASCRIPT, CodeLanguage.fromId("javascript"))
        assertEquals(CodeLanguage.JAVASCRIPT, CodeLanguage.fromId("js"))
        assertEquals(CodeLanguage.CSS, CodeLanguage.fromId("css"))
        assertEquals(CodeLanguage.PLAIN, CodeLanguage.fromId("plain"))
        assertEquals(CodeLanguage.PLAIN, CodeLanguage.fromId("unknown_custom_mode"))
        assertEquals(CodeLanguage.PLAIN, CodeLanguage.fromId(null))
    }

    @Test
    fun testCodeLanguageResolutionFromBodyFormat() {
        val jsonFormat = BodyFormat.Json("{\"status\":\"ok\"}")
        assertEquals(CodeLanguage.JSON, jsonFormat.toCodeLanguage())

        val gqlFormat = BodyFormat.GraphQL("Query", "GetUser", "query GetUser { id }", "{}")
        assertEquals(CodeLanguage.GRAPHQL, gqlFormat.toCodeLanguage())

        val xmlFormat = BodyFormat.Xml("<root><item>1</item></root>")
        assertEquals(CodeLanguage.XML, xmlFormat.toCodeLanguage())

        val htmlFormat = BodyFormat.Html("<!DOCTYPE html><html><body>Test</body></html>")
        assertEquals(CodeLanguage.HTML, htmlFormat.toCodeLanguage())

        val jsFormat = BodyFormat.Js("console.log('test');")
        assertEquals(CodeLanguage.JAVASCRIPT, jsFormat.toCodeLanguage())

        val cssFormat = BodyFormat.Css("body { color: red; }")
        assertEquals(CodeLanguage.CSS, cssFormat.toCodeLanguage())

        val rawFormat = BodyFormat.RawText("plain string")
        assertEquals(CodeLanguage.PLAIN, rawFormat.toCodeLanguage())
    }

    @Test
    fun testSmartBodyViewerFormatResolution() {
        // 1. JSON
        val jsonHeaders = mapOf("content-type" to "application/json")
        val jsonBody = "{\"data\": 123}"
        val jsonFormat = BodyFormatterRegistry.resolveFormat(jsonHeaders, jsonBody)
        assertIs<BodyFormat.Json>(jsonFormat)

        // 2. GraphQL JSON Envelope
        val gqlBody = "{\"query\": \"query GetQuotes { quotes { id text } }\"}"
        val gqlFormat = BodyFormatterRegistry.resolveFormat(jsonHeaders, gqlBody)
        assertIs<BodyFormat.GraphQL>(gqlFormat)
        assertEquals("GetQuotes", gqlFormat.operationName)

        // 3. Form Data
        val formHeaders = mapOf("content-type" to "application/x-www-form-urlencoded")
        val formBody = "grant_type=client_credentials&client_id=knet_dev"
        val formFormat = BodyFormatterRegistry.resolveFormat(formHeaders, formBody)
        assertIs<BodyFormat.FormData>(formFormat)
        assertEquals(2, formFormat.pairs.size)
        assertEquals("grant_type", formFormat.pairs[0].first)
        assertEquals("client_credentials", formFormat.pairs[0].second)

        // 4. XML
        val xmlHeaders = mapOf("content-type" to "application/xml")
        val xmlBody = "<response><status>OK</status></response>"
        val xmlFormat = BodyFormatterRegistry.resolveFormat(xmlHeaders, xmlBody)
        assertIs<BodyFormat.Xml>(xmlFormat)

        // 5. HTML
        val htmlHeaders = mapOf("content-type" to "text/html")
        val htmlBody = "<html><body><h1>Hello KNet</h1></body></html>"
        val htmlFormat = BodyFormatterRegistry.resolveFormat(htmlHeaders, htmlBody)
        assertIs<BodyFormat.Html>(htmlFormat)
    }
}
