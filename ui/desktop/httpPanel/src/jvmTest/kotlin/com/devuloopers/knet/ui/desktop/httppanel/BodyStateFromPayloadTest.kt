package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.ui.desktop.httppanel.model.BodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying [BodyState.fromPayload] automatic format detection
 * and payload model hydration across all supported HTTP payload formats.
 */
class BodyStateFromPayloadTest {

    @Test
    fun testEmptyOrBlankPayloadReturnsNoneMode() {
        val emptyState = BodyState.fromPayload(emptyList(), "")
        assertEquals(BodyMode.NONE, emptyState.mode)
        assertEquals("", emptyState.payloadText)

        val blankState = BodyState.fromPayload(emptyList(), "   \n  \t  ")
        assertEquals(BodyMode.NONE, blankState.mode)
        assertEquals("", blankState.payloadText)
    }

    @Test
    fun testGraphQlJsonPayloadAutoDetectsGraphQlModeAndHydratesState() {
        val headers = listOf("content-type" to "application/json")
        val graphQlJson = """
            {
              "query": "query FormattedQuotes(${'$'}symbols: [String]) { formattedQuotes(symbols: ${'$'}symbols) { symbol last_price } }",
              "operationName": "FormattedQuotes",
              "variables": {
                "symbols": [".N225", ".SSEC"]
              },
              "extensions": {
                "clientLibrary": { "name": "apollo-kotlin" }
              }
            }
        """.trimIndent()

        val state = BodyState.fromPayload(headers, graphQlJson)

        assertEquals(BodyMode.GRAPHQL, state.mode)
        assertEquals("FormattedQuotes", state.graphQlState.operationName)
        assertTrue(state.graphQlState.queryText.contains("formattedQuotes"))
        assertTrue(state.graphQlState.variablesText.contains(".N225"))
        assertTrue(state.graphQlState.extensionsText.contains("apollo-kotlin"))
    }

    @Test
    fun testFormDataPayloadAutoDetectsFormDataModeAndPopulatesEntries() {
        val headers = listOf("content-type" to "application/x-www-form-urlencoded")
        val formPayload = "grant_type=client_credentials&client_id=knet_client_123"

        val state = BodyState.fromPayload(headers, formPayload)

        assertEquals(BodyMode.FORM_DATA, state.mode)
        assertEquals(2, state.formDataEntries.size)
        assertEquals("grant_type", state.formDataEntries[0].key)
        assertEquals("client_credentials", state.formDataEntries[0].value)
        assertEquals("client_id", state.formDataEntries[1].key)
        assertEquals("knet_client_123", state.formDataEntries[1].value)
    }

    @Test
    fun testJsonPayloadAutoDetectsJsonMode() {
        val headers = listOf("content-type" to "application/json")
        val jsonPayload = """{"status":"active","count":42}"""

        val state = BodyState.fromPayload(headers, jsonPayload)

        assertEquals(BodyMode.JSON, state.mode)
        assertEquals(jsonPayload, state.payloadText)
    }

    @Test
    fun testXmlPayloadAutoDetectsRawXmlMode() {
        val headers = listOf("content-type" to "application/xml")
        val xmlPayload = "<response><status>success</status></response>"

        val state = BodyState.fromPayload(headers, xmlPayload)

        assertEquals(BodyMode.RAW, state.mode)
        assertEquals(RawSubFormat.XML, state.rawSubFormat)
        assertEquals(xmlPayload, state.payloadText)
    }

    @Test
    fun testHtmlPayloadAutoDetectsRawHtmlMode() {
        val headers = listOf("content-type" to "text/html")
        val htmlPayload = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>"

        val state = BodyState.fromPayload(headers, htmlPayload)

        assertEquals(BodyMode.RAW, state.mode)
        assertEquals(RawSubFormat.HTML, state.rawSubFormat)
        assertEquals(htmlPayload, state.payloadText)
    }

    @Test
    fun testPlainTextPayloadAutoDetectsRawTextMode() {
        val headers = listOf("content-type" to "text/plain")
        val plainText = "Hello KNet Desktop"

        val state = BodyState.fromPayload(headers, plainText)

        assertEquals(BodyMode.RAW, state.mode)
        assertEquals(RawSubFormat.TEXT, state.rawSubFormat)
        assertEquals(plainText, state.payloadText)
    }
}
