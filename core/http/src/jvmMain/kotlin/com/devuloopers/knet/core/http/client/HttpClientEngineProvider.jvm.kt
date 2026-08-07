package com.devuloopers.knet.core.http.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/**
 * JVM specific provider for Ktor's [HttpClientEngineFactory].
 * Uses OkHttp as it properly supports HTTP proxies with target domain delegation.
 */
actual fun getKNetHttpEngine(): HttpClientEngineFactory<*> = OkHttp
