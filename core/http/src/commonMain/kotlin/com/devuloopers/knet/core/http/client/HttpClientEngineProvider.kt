package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Multiplatform provider for Ktor's [HttpClientEngineFactory].
 * Allows specific platforms to inject their preferred networking engine.
 */
expect fun getKNetHttpEngine(): HttpClientEngineFactory<*>

/**
 * Multiplatform factory responsible for creating configured [HttpClient] instances with
 * proxy settings and unified SSL/TLS certificate trust policies.
 *
 * @param targetProxyPort Optional proxy port to route traffic through.
 * @param localProxyTlsTrust Optional certificate authority trusted only by a proxy-configured client.
 * @param configuration Global HTTP client timeout, retry, and SSL verification settings.
 * @param customEngine Optional custom engine (e.g. MockEngine for testing).
 * @param block Additional Ktor client configuration block.
 * @return Configured [HttpClient] instance.
 */
expect fun createPlatformHttpClient(
    targetProxyPort: Int?,
    localProxyTlsTrust: LocalProxyTlsTrust?,
    configuration: HttpClientConfiguration,
    customEngine: HttpClientEngine?,
    block: HttpClientConfig<*>.() -> Unit
): HttpClient
