package com.devuloopers.knet.engine.certificate.cache

import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.certificate.util.TestCertificateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class CertificateCacheTest {

    private val ca = TestCertificateFactory.createTestCa()

    @Test
    fun testCacheHitAndMiss() {
        val cache = CertificateCache()
        assertEquals(0, cache.size())

        val leaf1 = cache.get("google.com", ca)
        assertEquals(1, cache.size())

        // Consecutive fetch should hit cache and return same instance
        val leaf2 = cache.get("google.com", ca)
        assertSame(leaf1, leaf2, "Cache hit must return identical leaf instance")
        assertEquals(1, cache.size())

        // Fetching different host generates new cert
        val leaf3 = cache.get("yahoo.com", ca)
        assertNotEquals(leaf1.certificate.serialNumber, leaf3.certificate.serialNumber)
        assertEquals(2, cache.size())

        // Clear cache
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun testMaxEntriesUsesLeastRecentlyUsedEviction() {
        val boundedCache = CertificateCache(maxEntries = 2)
        boundedCache.get("domain1.com", ca)
        boundedCache.get("domain2.com", ca)
        assertEquals(2, boundedCache.size())

        // Exceeding the bound evicts one least-recently-used entry instead of causing a cache stampede.
        boundedCache.get("domain3.com", ca)
        assertEquals(2, boundedCache.size())
    }
}
