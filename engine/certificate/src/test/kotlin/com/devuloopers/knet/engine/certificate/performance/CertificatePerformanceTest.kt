package com.devuloopers.knet.engine.certificate.performance

import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.certificate.util.TestCertificateFactory
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class CertificatePerformanceTest {

    private val ca = TestCertificateFactory.createTestCa("Perf CA", "Perf Org")

    @Test
    fun testCachedLookupSpeedVsInitialGeneration() {
        val cache = CertificateCache()

        val generationTime = measureTimeMillis {
            cache.get("perf-domain.com", ca)
        }

        val cachedLookupTime = measureTimeMillis {
            for (i in 0 until 100) {
                cache.get("perf-domain.com", ca)
            }
        }

        assertTrue(cachedLookupTime < generationTime * 2, "100 cached lookups should be significantly faster than initial 2048-bit RSA generation")
    }
}
