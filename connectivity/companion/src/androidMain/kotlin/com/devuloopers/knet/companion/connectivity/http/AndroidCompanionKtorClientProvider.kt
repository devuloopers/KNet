package com.devuloopers.knet.companion.connectivity.http

import com.devuloopers.knet.companion.connectivity.certificate.AndroidPairedTlsTrustFactory
import com.devuloopers.knet.companion.connectivity.certificate.isServedByRoot
import com.devuloopers.knet.companion.connectivity.certificate.isValidPairingRoot
import com.devuloopers.knet.companion.connectivity.certificate.matchesPinnedTransportIdentity
import com.devuloopers.knet.companion.connectivity.certificate.parseX509Certificate
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.InetAddress
import java.security.cert.CertificateException
import java.security.cert.CertPathValidatorException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.Dns
import okhttp3.OkHttpClient

/** Android Ktor provider using a logical TLS authority routed to one paired LAN endpoint. */
internal class AndroidCompanionKtorClientProvider : CompanionKtorClientProvider {
    override fun create(request: CompanionHttpRequest): CompanionKtorClientHandle {
        val validation = request.security.toAndroidValidation()
        val logicalHost = request.tlsServerName
        val sessionFailure = java.util.concurrent.atomic.AtomicReference<com.devuloopers.knet.companion.application.contract.CompanionSessionFailure?>(null)
        val client = HttpClient(OkHttp) {
            configureCompanionClient(session = request.path == com.devuloopers.knet.companion.model.CompanionSessionProtocol.PATH, nativeWebSocketFraming = true)
            engine {
                config {
                    if (request.path == com.devuloopers.knet.companion.model.CompanionSessionProtocol.PATH) {
                        pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
                        // OkHttp reports non-401 upgrade failures as IOException, discarding the typed status
                        // before Ktor's response pipeline. Preserve only a closed reason, never response text.
                        addInterceptor { chain ->
                            chain.proceed(chain.request()).also { response ->
                                sessionFailure.set(when (response.code) {
                                    401, 403 -> com.devuloopers.knet.companion.application.contract.CompanionSessionFailure.UNAUTHORIZED
                                    404, 426 -> com.devuloopers.knet.companion.application.contract.CompanionSessionFailure.UNSUPPORTED
                                    else -> null
                                })
                            }
                        }
                    }
                    if (logicalHost != null) {
                        dns(PairedEndpointDns(logicalHost, request.endpoint.host))
                    }
                    validation?.configure(this)
                }
            }
        }
        return CompanionKtorClientHandle(
            client = client,
            requestHost = logicalHost,
            sessionFailure = { sessionFailure.get() },
            securityFailure = { failure -> validation?.classifyHandshakeFailure(failure) },
        )
    }

    private fun CompanionHttpSecurity.toAndroidValidation(): AndroidTlsValidation? = when (this) {
        CompanionHttpSecurity.BootstrapRootOnly -> null
        is CompanionHttpSecurity.PinnedRoot -> {
            val root = validatedRoot(rootCertificate.copyBytes(), rootCertificateSha256.value)
            AndroidTlsValidation(
                rootCertificate = root,
                transportIdentitySha256 = transportIdentitySha256.value,
                usePlatformTrust = false,
            )
        }
        is CompanionHttpSecurity.PlatformTrusted -> {
            val root = validatedRoot(expectedRootCertificate.copyBytes(), expectedRootCertificateSha256.value)
            AndroidTlsValidation(
                rootCertificate = root,
                transportIdentitySha256 = transportIdentitySha256.value,
                usePlatformTrust = true,
            )
        }
    }

    private fun validatedRoot(bytes: ByteArray, expectedSha256: String): X509Certificate =
        bytes.parseX509Certificate()
            ?.takeIf { certificate -> certificate.isValidPairingRoot(expectedSha256) }
            ?: throw CompanionHttpSecurityException.IdentityRejected()

    private class PairedEndpointDns(
        private val logicalHost: String,
        private val endpointHost: String,
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            if (hostname == logicalHost) {
                InetAddress.getAllByName(endpointHost).toList()
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
    }

    private data class AndroidTlsValidation(
        val rootCertificate: X509Certificate,
        val transportIdentitySha256: String,
        val usePlatformTrust: Boolean,
    ) {
        fun classifyHandshakeFailure(failure: Throwable): CompanionHttpSecurityException? =
            if (usePlatformTrust && failure.hasCertificateValidationCause()) {
                CompanionHttpSecurityException.TrustRejected()
            } else {
                null
            }

        fun configure(builder: OkHttpClient.Builder) {
            if (!usePlatformTrust) {
                val trustManager = AndroidPairedTlsTrustFactory.trustManager(rootCertificate)
                builder.sslSocketFactory(AndroidPairedTlsTrustFactory.socketFactory(rootCertificate), trustManager)
            }
            val platformHostnameVerifier = OkHttpClient().hostnameVerifier
            builder.hostnameVerifier { _, session ->
                val peerCertificates = try {
                    session.peerCertificates.filterIsInstance<X509Certificate>()
                } catch (_: SSLPeerUnverifiedException) {
                    return@hostnameVerifier false
                }
                if (!platformHostnameVerifier.verify(CompanionCertificateProtocol.TLS_SERVER_NAME, session)) {
                    return@hostnameVerifier false
                }
                if (
                    !peerCertificates.matchesPinnedTransportIdentity(transportIdentitySha256) ||
                    !peerCertificates.isServedByRoot(rootCertificate)
                ) {
                    return@hostnameVerifier false
                }
                true
            }
        }
    }
}

private fun Throwable.hasCertificateValidationCause(): Boolean {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is CertPathValidatorException || current is CertificateException) return true
        current = current.cause
    }
    return false
}
