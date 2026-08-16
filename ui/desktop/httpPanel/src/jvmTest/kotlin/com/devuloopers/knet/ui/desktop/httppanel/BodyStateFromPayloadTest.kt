package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying [RequestBodyState.from] and [ResponseBodyState.from]
 * mapping from pre-resolved [PayloadInspectionSpec] across all supported HTTP payload formats.
 */
class BodyStateFromPayloadTest {

    @Test
    fun testEmptyOrBlankPayloadReturnsNoneMode() {
        val emptyReqSpec = PayloadInspectionSpec.fromPayload(emptyList(), "")
        val emptyReqState = RequestBodyState.from(emptyReqSpec)
        assertEquals(RequestBodyMode.NONE, emptyReqState.mode)
        assertEquals("", emptyReqState.payloadText)

        val blankReqSpec = PayloadInspectionSpec.fromPayload(emptyList(), "   \n  \t  ")
        val blankReqState = RequestBodyState.from(blankReqSpec)
        assertEquals(RequestBodyMode.NONE, blankReqState.mode)
        assertEquals("", blankReqState.payloadText)

        val emptyRespSpec = PayloadInspectionSpec.fromPayload(emptyList(), "")
        val emptyRespState = ResponseBodyState.from(emptyRespSpec)
        assertEquals(ResponseBodyMode.NONE, emptyRespState.mode)
        assertEquals("", emptyRespState.payloadText)
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

        val spec = PayloadInspectionSpec.fromPayload(headers, graphQlJson)
        val state = RequestBodyState.from(spec)

        assertEquals(RequestBodyMode.GRAPHQL, state.mode)
        assertEquals("FormattedQuotes", state.graphQlState.operationName)
        assertTrue(state.graphQlState.queryText.contains("formattedQuotes"))
        assertTrue(state.graphQlState.variablesText.contains(".N225"))
        assertTrue(state.graphQlState.extensionsText.contains("apollo-kotlin"))
    }

    @Test
    fun testFormDataPayloadAutoDetectsFormDataModeAndPopulatesEntries() {
        val headers = listOf("content-type" to "application/x-www-form-urlencoded")
        val formPayload = "grant_type=client_credentials&client_id=knet_client_123"

        val spec = PayloadInspectionSpec.fromPayload(headers, formPayload)
        val state = RequestBodyState.from(spec)

        assertEquals(RequestBodyMode.FORM_DATA, state.mode)
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

        val spec = PayloadInspectionSpec.fromPayload(headers, jsonPayload)

        val reqState = RequestBodyState.from(spec)
        assertEquals(RequestBodyMode.JSON, reqState.mode)
        assertTrue(reqState.payloadText.contains("\n"), "JSON payload should be auto pretty-printed with newlines")
        assertTrue(reqState.payloadText.contains("\"status\": \"active\""))

        val respState = ResponseBodyState.from(spec)
        assertEquals(ResponseBodyMode.JSON, respState.mode)
        assertTrue(respState.payloadText.contains("\n"), "Response JSON should be auto pretty-printed with newlines")
    }

    @Test
    fun testXmlPayloadAutoDetectsRawXmlMode() {
        val headers = listOf("content-type" to "application/xml")
        val xmlPayload = "<response><status>success</status></response>"

        val spec = PayloadInspectionSpec.fromPayload(headers, xmlPayload)

        val reqState = RequestBodyState.from(spec)
        assertEquals(RequestBodyMode.RAW, reqState.mode)
        assertEquals(RawSubFormat.XML, reqState.rawSubFormat)
        assertTrue(reqState.payloadText.contains("\n"), "XML payload should be auto formatted with newlines")
        assertTrue(reqState.payloadText.contains("<status>success</status>"))

        val respState = ResponseBodyState.from(spec)
        assertEquals(ResponseBodyMode.XML, respState.mode)
        assertTrue(respState.payloadText.contains("<status>success</status>"))
    }

    @Test
    fun testHtmlPayloadAutoDetectsRawHtmlMode() {
        val headers = listOf("content-type" to "text/html")
        val htmlPayload = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>"

        val spec = PayloadInspectionSpec.fromPayload(headers, htmlPayload)

        val reqState = RequestBodyState.from(spec)
        assertEquals(RequestBodyMode.RAW, reqState.mode)
        assertEquals(RawSubFormat.HTML, reqState.rawSubFormat)
        assertTrue(reqState.payloadText.contains("<h1>"), "HTML payload should contain h1 tag")
        assertTrue(reqState.payloadText.contains("Hello"), "HTML payload should contain Hello")

        val respState = ResponseBodyState.from(spec)
        assertEquals(ResponseBodyMode.HTML, respState.mode)
        assertTrue(respState.payloadText.contains("<h1>"))
    }

    @Test
    fun testPlainTextPayloadAutoDetectsRawTextMode() {
        val headers = listOf("content-type" to "text/plain")
        val plainText = "Hello KNet Desktop"

        val spec = PayloadInspectionSpec.fromPayload(headers, plainText)

        val reqState = RequestBodyState.from(spec)
        assertEquals(RequestBodyMode.RAW, reqState.mode)
        assertEquals(RawSubFormat.TEXT, reqState.rawSubFormat)
        assertEquals(plainText, reqState.payloadText)

        val respState = ResponseBodyState.from(spec)
        assertEquals(ResponseBodyMode.TEXT, respState.mode)
        assertEquals(plainText, respState.payloadText)
    }
}
