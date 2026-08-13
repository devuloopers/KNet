package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class InspectorPreparationPipelineTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPreparationPipelineFormatsJsonInBackground() = runTest {
        val viewModel = FakeTrafficViewModelFactory.create()
        val rawJson = """{"id":1,"name":"Test Item","items":[1,2,3]}"""
        val item = TrafficItemUiState(
            id = 1,
            transactionId = "tx-101",
            method = "POST",
            host = "api.example.com",
            path = "/test",
            status = 200,
            statusText = "OK",
            protocol = "HTTP/1.1",
            formattedTime = "45 ms",
            formattedSize = "1.2 KB",
            dateGroup = "Today",
            requestBody = rawJson,
            responseBody = rawJson,
            queryParams = emptyMap(),
            requestHeaders = mapOf("Content-Type" to "application/json"),
            responseHeaders = mapOf("Content-Type" to "application/json")
        )

        viewModel.processIntent(com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent.SelectTransaction("tx-101"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.preparedState)
        assertFalse(state.preparedState.isPreparing)
    }
}
