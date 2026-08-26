package com.devuloopers.knet.companion.data.android

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Central execution boundary for synchronous Android storage and cryptographic APIs. */
internal class AndroidBlockingCallExecutor(
    private val dispatcher: CoroutineDispatcher,
) {
    /** Executes one synchronous platform operation on the configured worker dispatcher. */
    suspend fun <T> execute(operation: () -> T): T = withContext(dispatcher) { operation() }
}
