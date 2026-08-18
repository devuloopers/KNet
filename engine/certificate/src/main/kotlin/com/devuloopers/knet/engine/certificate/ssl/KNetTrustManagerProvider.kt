package com.devuloopers.knet.engine.certificate.ssl

import com.devuloopers.knet.core.logger.KNetLogger
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Unified provider for SSL/TLS Trust Managers and Trust Manager Factories across KNet.
 *
 * Serves as the single source of truth for both Netty's upstream proxy client and Ktor's
 * outbound HTTP client. It merges the standard JDK trust store (`cacerts`) with KNet's
 * local Root CA certificate (`~/.knet/ca/ca.crt`), ensuring seamless MITM decryption
 * and API Studio request dispatching without compromising cryptographic certificate verification.
 */
object KNetTrustManagerProvider {

    private const val TAG = "KNetTrustManagerProvider"
    private const val DEFAULT_CA_PATH = ".knet/ca/ca.crt"

    private val cachedCompositeTrustManager = AtomicReference<X509TrustManager?>(null)
    private val cachedCompositeTrustManagerFactory = AtomicReference<TrustManagerFactory?>(null)

    /**
     * Permissive trust manager that accepts all certificates when SSL verification is disabled.
     */
    val trustAllTrustManager: X509TrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    }

    /**
     * Retrieves an [X509TrustManager] instance based on the [verifySsl] flag.
     *
     * @param verifySsl If true, validates against standard public CAs and KNet Root CA.
     *                  If false, trusts all certificates for debugging.
     * @return Configured [X509TrustManager].
     */
    fun getX509TrustManager(verifySsl: Boolean): X509TrustManager {
        return if (verifySsl) {
            getOrCreateCompositeTrustManager()
        } else {
            trustAllTrustManager
        }
    }

    /**
     * Retrieves a [TrustManagerFactory] instance based on the [verifySsl] flag.
     *
     * @param verifySsl If true, validates against standard public CAs and KNet Root CA.
     *                  If false, trusts all certificates for debugging.
     * @return Configured [TrustManagerFactory].
     */
    fun getTrustManagerFactory(verifySsl: Boolean): TrustManagerFactory {
        return if (verifySsl) {
            getOrCreateCompositeTrustManagerFactory()
        } else {
            createInsecureTrustManagerFactory()
        }
    }

    /**
     * Invalidates any cached trust managers, forcing them to be reloaded on the next lookup
     * (e.g. after generating or regenerating a new Root CA).
     */
    fun invalidateCache() {
        cachedCompositeTrustManager.set(null)
        cachedCompositeTrustManagerFactory.set(null)
        KNetLogger.debug(TAG) { "Trust manager cache invalidated" }
    }

    private fun getOrCreateCompositeTrustManager(): X509TrustManager {
        val existing = cachedCompositeTrustManager.get()
        if (existing != null) return existing

        val factory = getOrCreateCompositeTrustManagerFactory()
        val trustManager = checkNotNull(
            factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        ) {
            "The platform trust manager factory did not provide an X509 trust manager."
        }

        cachedCompositeTrustManager.compareAndSet(null, trustManager)
        return cachedCompositeTrustManager.get() ?: trustManager
    }

    private fun getOrCreateCompositeTrustManagerFactory(): TrustManagerFactory {
        val existing = cachedCompositeTrustManagerFactory.get()
        if (existing != null) return existing

        val compositeKeyStore = buildCompositeKeyStore()
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(compositeKeyStore)

        cachedCompositeTrustManagerFactory.compareAndSet(null, factory)
        return cachedCompositeTrustManagerFactory.get() ?: factory
    }

    private fun createInsecureTrustManagerFactory(): TrustManagerFactory {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        val insecureKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        insecureKeyStore.load(null, null)

        return object : TrustManagerFactory(
            object : javax.net.ssl.TrustManagerFactorySpi() {
                override fun engineInit(ks: KeyStore?) {}
                override fun engineInit(spec: javax.net.ssl.ManagerFactoryParameters?) {}
                override fun engineGetTrustManagers(): Array<TrustManager> = arrayOf(trustAllTrustManager)
            },
            factory.provider,
            factory.algorithm
        ) {}
    }

    /**
     * Loads the default JDK `cacerts` store and imports KNet's local Root CA certificate if present.
     */
    private fun buildCompositeKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        // 1. Load default system/JDK CA certificates
        try {
            val defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            defaultTmf.init(null as KeyStore?)
            val defaultX509 = defaultTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()

            defaultX509?.acceptedIssuers?.forEachIndexed { index, issuerCert ->
                keyStore.setCertificateEntry("system_ca_$index", issuerCert)
            }
        } catch (e: Exception) {
            KNetLogger.warn(TAG) { "Failed to load default system cacerts into composite trust store: ${e.message}" }
        }

        // 2. Import KNet local Root CA certificate if it exists on disk
        try {
            val userHome = System.getProperty("user.home") ?: ""
            val caFile = File(userHome, DEFAULT_CA_PATH)
            if (caFile.exists() && caFile.canRead()) {
                FileInputStream(caFile).use { inputStream ->
                    val certFactory = CertificateFactory.getInstance("X.509")
                    val caCert = certFactory.generateCertificate(inputStream) as X509Certificate
                    keyStore.setCertificateEntry("knet_root_ca", caCert)
                    KNetLogger.info(TAG) { "Successfully loaded KNet Root CA into unified trust store: ${caCert.subjectX500Principal.name}" }
                }
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to import KNet Root CA into composite trust store" }
        }

        return keyStore
    }
}
