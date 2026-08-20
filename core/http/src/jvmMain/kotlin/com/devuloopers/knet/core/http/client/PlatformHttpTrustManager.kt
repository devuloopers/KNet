package com.devuloopers.knet.core.http.client

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** JVM HTTP-client trust policy with an optional, explicitly scoped local-proxy certificate authority. */
internal object PlatformHttpTrustManager {
    /**
     * Resolves the strict or explicitly insecure trust manager for one client.
     *
     * Strict direct clients use only platform roots. Strict proxy clients additionally trust the
     * supplied local proxy CA without changing the process trust store or proxy-to-origin policy.
     */
    fun get(
        verifySsl: Boolean,
        localProxyTlsTrust: LocalProxyTlsTrust? = null,
    ): X509TrustManager {
        if (!verifySsl) return InsecureX509TrustManager
        val platformTrustManager = trustManagerFor(keyStore = null)
        if (localProxyTlsTrust == null) return platformTrustManager
        return CompositeX509TrustManager(
            delegates = listOf(
                platformTrustManager,
                localProxyTrustManager(localProxyTlsTrust),
            )
        )
    }

    /** Creates a trust manager whose only anchor is the validated local proxy CA. */
    private fun localProxyTrustManager(trust: LocalProxyTlsTrust): X509TrustManager {
        val certificate = parseCertificateAuthority(trust.certificateAuthorityDerCopy())
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry(LOCAL_PROXY_CA_ALIAS, certificate)
        }
        return trustManagerFor(keyStore)
    }

    /** Parses and validates one self-signed CA certificate before it can become a trust anchor. */
    private fun parseCertificateAuthority(encoded: ByteArray): X509Certificate = try {
        val certificate = ByteArrayInputStream(encoded).use { input ->
            val parsed = CertificateFactory.getInstance(X509_CERTIFICATE_TYPE)
                .generateCertificate(input) as? X509Certificate
                ?: throw CertificateException("Local proxy trust material is not an X.509 certificate.")
            if (input.available() != 0) {
                throw CertificateException("Local proxy trust material contains trailing data.")
            }
            parsed
        }
        certificate.checkValidity()
        require(certificate.basicConstraints >= 0) {
            "Local proxy trust certificate must be a certificate authority."
        }
        require(certificate.subjectX500Principal == certificate.issuerX500Principal) {
            "Local proxy trust certificate must be self-signed."
        }
        certificate.verify(certificate.publicKey)
        certificate
    } catch (failure: Exception) {
        throw IllegalArgumentException("Local proxy certificate authority is invalid.", failure)
    }

    /** Extracts the single X.509 trust manager created by the platform factory. */
    private fun trustManagerFor(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        return factory.trustManagers.filterIsInstance<X509TrustManager>().singleOrNull()
            ?: error("The JVM did not provide exactly one X509TrustManager.")
    }

    private object InsecureX509TrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private const val X509_CERTIFICATE_TYPE = "X.509"
    private const val LOCAL_PROXY_CA_ALIAS = "local-proxy-ca"
}

/** Delegates complete certificate-chain validation to independent trust domains. */
private class CompositeX509TrustManager(
    private val delegates: List<X509TrustManager>,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkTrusted { delegate -> delegate.checkClientTrusted(chain, authType) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkTrusted { delegate -> delegate.checkServerTrusted(chain, authType) }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegates.flatMap { delegate -> delegate.acceptedIssuers.asIterable() }.toTypedArray()

    /** Accepts only when one complete trust domain validates the chain without weakening either. */
    private inline fun checkTrusted(validate: (X509TrustManager) -> Unit) {
        val failures = mutableListOf<CertificateException>()
        delegates.forEach { delegate ->
            try {
                validate(delegate)
                return
            } catch (failure: CertificateException) {
                failures += failure
            }
        }
        throw CertificateException(
            "Certificate chain is not trusted by platform roots or the configured local proxy authority.",
            failures.lastOrNull(),
        ).also { rejected ->
            failures.dropLast(1).forEach(rejected::addSuppressed)
        }
    }
}
