package com.devuloopers.knet.ui.apistudio

import com.devuloopers.knet.ui.apistudio.viewmodel.ApiStudioViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiStudioExecutionTest {

    @Test
    fun testUrlNormalizationPrependsHttp() {
        val viewModel = ApiStudioViewModel()

        assertEquals("http://127.0.0.1:9090/api/test/get", viewModel.normalizeUrl("127.0.0.1:9090/api/test/get"))
        assertEquals("http://localhost:9090", viewModel.normalizeUrl("localhost:9090"))
        assertEquals("https://httpbin.org/post", viewModel.normalizeUrl("https://httpbin.org/post"))
        assertEquals("http://httpbin.org/get", viewModel.normalizeUrl("http://httpbin.org/get"))
    }

    @Test
    fun testUrlChangeResetsResponseState() {
        val viewModel = ApiStudioViewModel()

        // Simulate typing new URL
        viewModel.onUrlInputChanged("http://127.0.0.1:9090/new-endpoint")

        val state = viewModel.uiState.value
        assertNull(state.latestResult, "latestResult should be cleared when URL is edited")
        assertTrue(state.testResults.isEmpty(), "testResults should be cleared when URL is edited")
        assertNull(state.scriptErrorMessage, "scriptErrorMessage should be cleared when URL is edited")
    }
}
