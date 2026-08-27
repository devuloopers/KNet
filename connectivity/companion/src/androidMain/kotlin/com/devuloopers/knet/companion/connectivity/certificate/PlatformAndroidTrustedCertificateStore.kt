package com.devuloopers.knet.companion.connectivity.certificate

import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks up the exact paired certificate afresh so TLS session reuse cannot hide its removal. */
internal class PlatformAndroidTrustedCertificateStore(
    private val trustedCertificatesLoader: () -> Sequence<X509Certificate> = ::loadAndroidCaCertificates,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : AndroidTrustedCertificateStore {
    override suspend fun lookup(
        rootCertificate: X509Certificate,
    ): AndroidTrustedCertificateLookupResult = withContext(ioContext) {
        try {
            val trustedCertificates = trustedCertificatesLoader()
            val expectedSha256 = rootCertificate.sha256Hex()
            val exactMatch = trustedCertificates.any { certificate ->
                certificate.sha256Hex() == expectedSha256
            }
            if (exactMatch) {
                AndroidTrustedCertificateLookupResult.Present
            } else {
                AndroidTrustedCertificateLookupResult.Absent
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AndroidTrustedCertificateLookupResult.Unavailable
        }
    }
}

private fun loadAndroidCaCertificates(): Sequence<X509Certificate> {
    val keyStore = KeyStore.getInstance(ANDROID_CA_STORE).apply { load(null) }
    return sequence {
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val certificate = keyStore.getCertificate(aliases.nextElement()) as? X509Certificate ?: continue
            yield(certificate)
        }
    }
}

private const val ANDROID_CA_STORE: String = "AndroidCAStore"
