package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.engine.certificate.ssl.KNetTrustManagerProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.http.Url

/**
 * JVM specific provider for Ktor's [HttpClientEngineFactory].
 * Uses CIO (Coroutine I/O) engine.
 */
actual fun getKNetHttpEngine(): HttpClientEngineFactory<*> = CIO

/**
 * JVM implementation creating a CIO [HttpClient] configured with proxy routing and
 * unified SSL trust manager from [KNetTrustManagerProvider].
 */
actual fun createPlatformHttpClient(
    targetProxyPort: Int?,
    configuration: HttpClientConfiguration,
    customEngine: HttpClientEngine?,
    block: HttpClientConfig<*>.() -> Unit
): HttpClient {
    if (customEngine != null) {
        return HttpClient(customEngine, block)
    }

    return HttpClient(CIO) {
        engine {
            if (targetProxyPort != null && targetProxyPort > 0) {
                proxy = ProxyBuilder.http(Url("http://127.0.0.1:$targetProxyPort"))
            }
            https {
                trustManager = KNetTrustManagerProvider.getX509TrustManager(
                    verifySsl = configuration.verifySsl
                )
            }
        }
        block()
    }
}
