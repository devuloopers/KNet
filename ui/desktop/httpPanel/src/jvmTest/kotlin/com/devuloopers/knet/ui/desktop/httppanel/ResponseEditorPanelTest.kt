package com.devuloopers.knet.ui.desktop.httppanel

import com.devuloopers.knet.ui.desktop.httppanel.editor.ResponseEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState
import kotlin.test.Test
import kotlin.test.assertEquals

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
        actions.onBodyStateChanged(ResponseBodyState(mode = ResponseBodyMode.JSON, payloadText = "{\"error\":\"not found\"}"))
        actions.onHeadersChanged(listOf("Content-Type" to "application/json"))
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
        val jsonState = ResponseBodyState.fromPayload(jsonHeaders, "{\"status\": \"ok\"}")
        assertEquals(ResponseBodyMode.JSON, jsonState.mode)

        val xmlHeaders = listOf("content-type" to "application/xml")
        val xmlState = ResponseBodyState.fromPayload(xmlHeaders, "<response><status>ok</status></response>")
        assertEquals(ResponseBodyMode.XML, xmlState.mode)

        val htmlHeaders = listOf("content-type" to "text/html")
        val htmlState = ResponseBodyState.fromPayload(htmlHeaders, "<html><body>Hello</body></html>")
        assertEquals(ResponseBodyMode.HTML, htmlState.mode)

        val textHeaders = listOf("content-type" to "text/plain")
        val textState = ResponseBodyState.fromPayload(textHeaders, "OK")
        assertEquals(ResponseBodyMode.TEXT, textState.mode)

        val emptyState = ResponseBodyState.fromPayload(emptyList(), "")
        assertEquals(ResponseBodyMode.NONE, emptyState.mode)
    }
}
