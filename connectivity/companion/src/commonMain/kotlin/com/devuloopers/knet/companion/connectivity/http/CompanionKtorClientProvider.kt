package com.devuloopers.knet.companion.connectivity.http

import io.ktor.client.HttpClient

/** Creates one request-scoped Ktor client whose engine enforces the supplied TLS policy. */
internal fun interface CompanionKtorClientProvider {
    fun create(request: CompanionHttpRequest): CompanionKtorClientHandle
}

/** Request-scoped client plus native handshake failure state unavailable through some Ktor engines. */
internal class CompanionKtorClientHandle(
    val client: HttpClient,
    val requestHost: String? = null,
    private val securityFailure: (Throwable) -> CompanionHttpSecurityException? = { null },
    private val releasePlatformResources: () -> Unit = {},
) {
    fun securityFailure(failure: Throwable): CompanionHttpSecurityException? = securityFailure.invoke(failure)

    fun close() {
        try {
            client.close()
        } finally {
            releasePlatformResources.invoke()
        }
    }
}
