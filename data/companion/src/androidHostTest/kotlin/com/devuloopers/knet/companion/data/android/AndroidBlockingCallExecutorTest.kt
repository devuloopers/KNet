package com.devuloopers.knet.companion.data.android

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidBlockingCallExecutorTest {
    @Test
    fun `blocking operation executes on configured worker dispatcher`() = runTest {
        val dispatcher = Executors.newSingleThreadExecutor { operation ->
            Thread(operation, WORKER_THREAD_NAME)
        }.asCoroutineDispatcher()

        try {
            val executedThread = AndroidBlockingCallExecutor(dispatcher).execute {
                Thread.currentThread().name
            }

            assertEquals(WORKER_THREAD_NAME, executedThread)
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `blocking operation preserves platform failure`() = runTest {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        try {
            val failure = assertFailsWith<IllegalStateException> {
                AndroidBlockingCallExecutor(dispatcher).execute {
                    error(EXPECTED_FAILURE)
                }
            }

            assertEquals(EXPECTED_FAILURE, failure.message)
        } finally {
            dispatcher.close()
        }
    }

    private companion object {
        const val WORKER_THREAD_NAME: String = "companion-android-blocking-test"
        const val EXPECTED_FAILURE: String = "platform failure"
    }
}
