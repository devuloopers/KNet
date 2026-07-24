package com.devuloopers.knet.crypto

import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, in-memory cache to store dynamically signed leaf certificates.
 * Caching certificates prevents signing latencies from slowing down incoming TLS connection handshakes.
 */
class CertificateCache {

    private val cache = ConcurrentHashMap<String, LeafCertificate>()

    /**
     * Retrieves a certificate for the target hostname. If the certificate does not exist in
     * the cache or has expired, a fresh one is generated, cached, and returned.
     *
     * @param hostname The target hostname.
     * @param ca The Certificate Authority whose keys will be used if generation is required.
     * @return A valid [LeafCertificate] bundle.
     */
    fun get(hostname: String, ca: CertificateAuthority): LeafCertificate {
        val cached = cache[hostname]
        if (cached != null && isCertificateValid(cached.certificate)) {
            return cached
        }

        // Cache miss or expired certificate, generate a new one
        val fresh = LeafCertificateGenerator.generate(hostname, ca)
        cache[hostname] = fresh
        return fresh
    }

    /**
     * Clears all cached certificates.
     */
    fun clear() {
        cache.clear()
    }

    /**
     * Returns the total number of certificates stored in the cache.
     *
     * @return Cache size.
     */
    fun size(): Int {
        return cache.size
    }

    /**
     * Checks if the certificate is currently within its validity period.
     *
     * @param certificate The certificate to check.
     * @return True if the certificate is valid, false if it has expired or is not yet valid.
     */
    private fun isCertificateValid(certificate: X509Certificate): Boolean {
        return try {
            certificate.checkValidity()
            true
        } catch (e: Exception) {
            false
        }
    }
}
