package com.devuloopers.knet.domain.network

import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.GetLocalIpUseCase
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLocalIpUseCaseTest {

    @Test
    fun `ObserveLocalIpUseCase executes repository observation flow`() = runTest {
        val fakeRepo = object : NetworkRepository {
            override fun observeLocalIp(pollIntervalMs: Long): Flow<String> = flowOf("192.168.1.50")
            override suspend fun getLocalIp(): String = "192.168.1.50"
        }

        val useCase = ObserveLocalIpUseCase(fakeRepo)
        val result = useCase.execute().first()
        assertEquals("192.168.1.50", result)
    }

    @Test
    fun `GetLocalIpUseCase returns single-shot IP address`() = runTest {
        val fakeRepo = object : NetworkRepository {
            override fun observeLocalIp(pollIntervalMs: Long): Flow<String> = flowOf("10.0.0.1")
            override suspend fun getLocalIp(): String = "10.0.0.1"
        }

        val useCase = GetLocalIpUseCase(fakeRepo)
        val result = useCase.execute()
        assertEquals("10.0.0.1", result)
    }
}
