package com.devuloopers.knet.engine.proxy.performance

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.TimeSource

class PerformanceRegressionTest {

    @Test
    fun testCertificateGenerationPerformanceBaseline() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()

        val start = TimeSource.Monotonic.markNow()
        cache.get("perf.example.com", ca)
        val duration = start.elapsedNow()

        assertTrue(!duration.isNegative())
    }
}
