package com.devuloopers.knet.engine.certificate

import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Thread-safe, in-memory cache to store dynamically signed leaf certificates.
 * Caching certificates prevents signing latencies from slowing down incoming TLS connection handshakes.
 *
 * Cache misses are single-flight per normalized hostname, including asynchronous callers. Entries
 * expire after access and are evicted least-recently-used under both count and estimated byte bounds.
 *
 * @property maxEntries Maximum cached leaf count.
 * @property maximumWeightBytes Maximum estimated encoded certificate/key bytes retained.
 * @property expireAfterAccessMillis Idle lifetime of one cached leaf.
 */
class CertificateCache(
    private val maxEntries: Int = 1_000,
    private val maximumWeightBytes: Long = 16L * 1_024L * 1_024L,
    private val expireAfterAccessMillis: Long = 60L * 60L * 1_000L,
) {
    private val cacheLock = Any()
    private val cache = LinkedHashMap<String, CachedLeaf>(16, 0.75f, true)
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<LeafCertificate>>()
    private var cachedWeightBytes: Long = 0L

    init {
        require(maxEntries > 0) { "Certificate cache entry limit must be positive." }
        require(maximumWeightBytes > 0L) { "Certificate cache weight limit must be positive." }
        require(expireAfterAccessMillis > 0L) { "Certificate cache expiry must be positive." }
    }

    /**
     * Retrieves a certificate for the target hostname. If the certificate does not exist in
     * the cache or has expired, a fresh one is generated, cached, and returned.
     *
     * @param hostname The target hostname.
     * @param ca The Certificate Authority whose keys will be used if generation is required.
     * @return A valid [LeafCertificate] bundle.
     */
    fun get(hostname: String, ca: CertificateAuthority): LeafCertificate {
        val key = normalizeHostname(hostname)
        cachedLeaf(key)?.let { return it }
        val created = CompletableFuture<LeafCertificate>()
        val current = inFlight.putIfAbsent(key, created)
        if (current != null) return current.join()
        return try {
            generateAndCache(key, ca).also(created::complete)
        } catch (failure: Throwable) {
            created.completeExceptionally(failure)
            throw failure
        } finally {
            inFlight.remove(key, created)
        }
    }

    /**
     * Resolves or generates a leaf without running certificate work on the caller's thread.
     * Concurrent misses for the same host share one future and therefore one key-generation task.
     */
    fun getAsync(
        hostname: String,
        ca: CertificateAuthority,
        executor: Executor,
    ): CompletableFuture<LeafCertificate> {
        val key = normalizeHostname(hostname)
        cachedLeaf(key)?.let { cached -> return CompletableFuture.completedFuture(cached) }
        val created = CompletableFuture<LeafCertificate>()
        val current = inFlight.putIfAbsent(key, created)
        if (current != null) return current
        try {
            executor.execute {
                try {
                    created.complete(generateAndCache(key, ca))
                } catch (failure: Throwable) {
                    created.completeExceptionally(failure)
                } finally {
                    inFlight.remove(key, created)
                }
            }
        } catch (failure: Throwable) {
            inFlight.remove(key, created)
            created.completeExceptionally(failure)
        }
        return created
    }

    /**
     * Clears all cached certificates.
     */
    fun clear() {
        synchronized(cacheLock) {
            cache.clear()
            cachedWeightBytes = 0L
        }
    }

    /**
     * Returns the total number of certificates stored in the cache.
     *
     * @return Cache size.
     */
    fun size(): Int {
        return synchronized(cacheLock) { cache.size }
    }

    /** Returns a valid cached leaf and refreshes its access timestamp, or removes a stale entry. */
    private fun cachedLeaf(hostname: String): LeafCertificate? = synchronized(cacheLock) {
        val cached = cache[hostname] ?: return@synchronized null
        val idleMillis = cached.lastAccess.elapsedNow().inWholeMilliseconds
        if (idleMillis >= expireAfterAccessMillis || !isCertificateValid(cached.leaf.certificate)) {
            removeCached(hostname, cached)
            return@synchronized null
        }
        cache[hostname] = cached.copy(lastAccess = TimeSource.Monotonic.markNow())
        cached.leaf
    }

    /** Generates one leaf and installs it into the weighted LRU cache. */
    private fun generateAndCache(hostname: String, ca: CertificateAuthority): LeafCertificate {
        val leaf = LeafCertificateGenerator.generate(hostname, ca)
        val weight = estimateWeight(leaf)
        synchronized(cacheLock) {
            cache.remove(hostname)?.let { replaced -> cachedWeightBytes -= replaced.weightBytes }
            cache[hostname] = CachedLeaf(leaf, weight, TimeSource.Monotonic.markNow())
            cachedWeightBytes += weight
            evictToLimits()
        }
        return leaf
    }

    /** Evicts least-recently-used entries until both configured cache limits hold. */
    private fun evictToLimits() {
        val entries = cache.entries.iterator()
        while ((cache.size > maxEntries || cachedWeightBytes > maximumWeightBytes) && entries.hasNext()) {
            val entry = entries.next()
            cachedWeightBytes -= entry.value.weightBytes
            entries.remove()
        }
    }

    private fun removeCached(hostname: String, cached: CachedLeaf) {
        if (cache.remove(hostname) != null) cachedWeightBytes -= cached.weightBytes
    }

    /** Uses encoded material sizes as a stable cache-weight approximation. */
    private fun estimateWeight(leaf: LeafCertificate): Long {
        val certificateBytes = runCatching { leaf.certificate.encoded.size.toLong() }.getOrDefault(0L)
        val publicKeyBytes = leaf.keyPair.public.encoded?.size?.toLong() ?: 0L
        val privateKeyBytes = leaf.keyPair.private.encoded?.size?.toLong() ?: 0L
        return (certificateBytes + publicKeyBytes + privateKeyBytes).coerceAtLeast(1L)
    }

    private fun normalizeHostname(hostname: String): String {
        require(hostname.isNotBlank()) { "Certificate hostname must not be blank." }
        return hostname.trim().lowercase()
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
        } catch (_: Exception) {
            false
        }
    }

    private data class CachedLeaf(
        val leaf: LeafCertificate,
        val weightBytes: Long,
        val lastAccess: TimeMark,
    )
}
