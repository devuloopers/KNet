package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for RequestEditorState in `:ui:desktop:apiStudio`.
 */
class RequestEditorTest {

    @Test
    fun `RequestEditorState default values are set`() {
        val state = RequestEditorState()
        assertEquals("", state.url)
        assertEquals(HttpMethod.GET, state.method)
        assertEquals(HttpVersionPreference.AUTO, state.httpVersionPreference)
        assertEquals(
            com.devuloopers.knet.ui.desktop.httppanel.model.AuthType.NO_AUTH,
            state.authState.authType
        )
        assertEquals(RequestBodyMode.NONE, state.bodyState.mode)
    }
}
