package com.devuloopers.knet.core.http.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

/**
 * JVM specific provider for Ktor's [HttpClientEngineFactory].
 * Uses CIO (Coroutine I/O) engine.
 */
actual fun getKNetHttpEngine(): HttpClientEngineFactory<*> = CIO
