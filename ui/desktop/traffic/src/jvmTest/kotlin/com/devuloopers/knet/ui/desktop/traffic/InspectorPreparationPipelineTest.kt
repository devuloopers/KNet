package com.devuloopers.knet.ui.desktop.traffic

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
        viewModel.processIntent(com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent.SelectTransaction("tx-101"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.preparedState)
        assertFalse(state.preparedState.isPreparing)
    }
}
