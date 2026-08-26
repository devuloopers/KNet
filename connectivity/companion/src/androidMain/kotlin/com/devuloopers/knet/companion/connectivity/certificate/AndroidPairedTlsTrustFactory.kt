package com.devuloopers.knet.companion.connectivity.certificate

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Platform TLS material derived from one validated, dynamically paired KNet root. */
internal object AndroidPairedTlsTrustFactory {
    fun socketFactory(rootCertificate: X509Certificate): SSLSocketFactory = SSLContext.getInstance("TLS").run {
        init(null, trustManagers(rootCertificate), SecureRandom())
        socketFactory
    }

    fun trustManager(rootCertificate: X509Certificate): X509TrustManager =
        trustManagers(rootCertificate).filterIsInstance<X509TrustManager>().single()

    fun trustManagers(rootCertificate: X509Certificate): Array<TrustManager> {
        require(rootCertificate.isValidRootCertificate()) {
            "Paired TLS trust requires a currently valid, self-signed CA certificate."
        }
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry(PAIRED_ROOT_ALIAS, rootCertificate)
        }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
            init(trustStore)
            trustManagers
        }
    }

    private const val PAIRED_ROOT_ALIAS: String = "paired-knet-root"
}
