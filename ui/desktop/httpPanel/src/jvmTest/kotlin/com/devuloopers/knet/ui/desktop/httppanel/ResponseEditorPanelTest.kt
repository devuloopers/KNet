package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.ui.desktop.httppanel.editor.ResponseEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseEditorPanelTest {

    @Test
    fun testResponseEditorPanelActionsDefaultInvocation() {
        var statusCode = 200
        var statusText = "OK"
        var bodyState = ResponseBodyState()
        var headers = emptyList<Pair<String, String>>()
        var cookies = emptyList<Pair<String, String>>()
        var activeSubTab = InspectorSubTab.BODY

        val actions = ResponseEditorPanelActions(
            onStatusCodeChanged = { statusCode = it },
            onStatusTextChanged = { statusText = it },
            onBodyStateChanged = { bodyState = it },
            onHeadersChanged = { headers = it },
            onCookiesChanged = { cookies = it },
            onSubTabSelected = { activeSubTab = it }
        )

        actions.onStatusCodeChanged(404)
        actions.onStatusTextChanged("Not Found")
        actions.onBodyStateChanged(ResponseBodyState(mode = ResponseBodyMode.JSON))
        actions.onHeadersChanged(listOf("content-type" to "application/json"))
        actions.onCookiesChanged(listOf("session" to "xyz"))
        actions.onSubTabSelected(InspectorSubTab.HEADERS)

        assertEquals(404, statusCode)
        assertEquals("Not Found", statusText)
        assertEquals(ResponseBodyMode.JSON, bodyState.mode)
        assertEquals(1, headers.size)
        assertEquals("session", cookies.first().first)
        assertEquals(InspectorSubTab.HEADERS, activeSubTab)
    }

    @Test
    fun testResponseBodyModeResolutionFromPayload() {
        val jsonHeaders = listOf("content-type" to "application/json")
        val jsonState = ResponseBodyState.from(PayloadInspectionSpec.fromPayload(jsonHeaders, "{\"status\": \"ok\"}"))
        assertEquals(ResponseBodyMode.JSON, jsonState.mode)

        val xmlHeaders = listOf("content-type" to "application/xml")
        val xmlState = ResponseBodyState.from(PayloadInspectionSpec.fromPayload(xmlHeaders, "<response><status>ok</status></response>"))
        assertEquals(ResponseBodyMode.XML, xmlState.mode)

        val htmlHeaders = listOf("content-type" to "text/html")
        val htmlState = ResponseBodyState.from(PayloadInspectionSpec.fromPayload(htmlHeaders, "<html><body>Hello</body></html>"))
        assertEquals(ResponseBodyMode.HTML, htmlState.mode)

        val textHeaders = listOf("content-type" to "text/plain")
        val textState = ResponseBodyState.from(PayloadInspectionSpec.fromPayload(textHeaders, "OK"))
        assertEquals(ResponseBodyMode.TEXT, textState.mode)

        val emptyState = ResponseBodyState.from(PayloadInspectionSpec.fromPayload(emptyList(), ""))
        assertEquals(ResponseBodyMode.NONE, emptyState.mode)
    }

    @Test
    fun testResponseBodyModePrettify() {
        val rawJson = "{\"status\":\"ok\",\"code\":200}"
        val prettyJson = ResponseBodyMode.JSON.prettify(rawJson)
        assertEquals(true, ResponseBodyMode.JSON.isPrettifiable)
        assertEquals("{\n  \"status\": \"ok\",\n  \"code\": 200\n}", prettyJson)

        val rawXml = "<response><status>ok</status></response>"
        val prettyXml = ResponseBodyMode.XML.prettify(rawXml)
        assertEquals(true, ResponseBodyMode.XML.isPrettifiable)
        assertTrue(prettyXml.contains("<status>ok</status>"))

        val rawHtml = "<html><body><h1>200</h1></body></html>"
        val prettyHtml = ResponseBodyMode.HTML.prettify(rawHtml)
        assertEquals(true, ResponseBodyMode.HTML.isPrettifiable)
        assertTrue(prettyHtml.contains("<h1>"))
        assertTrue(prettyHtml.contains("200"))

        assertEquals(false, ResponseBodyMode.TEXT.isPrettifiable)
        assertEquals("plain text", ResponseBodyMode.TEXT.prettify("plain text"))

        assertEquals(false, ResponseBodyMode.RAW.isPrettifiable)
        assertEquals("raw data", ResponseBodyMode.RAW.prettify("raw data"))
    }

    @Test
    fun testRawSubFormatPrettify() {
        val rawJson = "{\"key\":\"value\"}"
        assertEquals(true, com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat.JSON.isPrettifiable)
        assertEquals("{\n  \"key\": \"value\"\n}", com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat.JSON.prettify(rawJson))

        val rawXml = "<root><item>1</item></root>"
        assertEquals(true, com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat.XML.isPrettifiable)
        assertTrue(com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat.XML.prettify(rawXml).contains("<item>1</item>"))

        assertEquals(false, com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat.TEXT.isPrettifiable)
        assertEquals("some text", com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat.TEXT.prettify("some text"))
    }
}
