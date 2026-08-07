package com.devuloopers.knet.core.http.client

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Multiplatform provider for Ktor's [HttpClientEngineFactory].
 * Allows specific platforms to inject their preferred networking engine.
 */
expect fun getKNetHttpEngine(): HttpClientEngineFactory<*>
