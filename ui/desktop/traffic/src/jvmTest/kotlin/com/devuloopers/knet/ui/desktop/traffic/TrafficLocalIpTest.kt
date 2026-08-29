package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficLocalIpTest {

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
    fun `TrafficViewModel collects and updates localIpAddress state from ObserveLocalIpUseCase`() = runTest {
        val fakeNetworkRepo = object : NetworkRepository {
            override fun observeLocalIp(): Flow<String> = flowOf("192.168.1.100")
            override suspend fun getLocalIp(): String = "192.168.1.100"
        }
        val observeLocalIpUseCase = ObserveLocalIpUseCase(fakeNetworkRepo)

        val viewModel = FakeTrafficViewModelFactory.create(
            customObserveLocalIpUseCase = observeLocalIpUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("192.168.1.100", viewModel.uiState.value.localIpAddress)
    }
}
