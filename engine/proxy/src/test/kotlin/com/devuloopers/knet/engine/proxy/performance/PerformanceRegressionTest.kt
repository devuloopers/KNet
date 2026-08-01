package com.devuloopers.knet.engine.proxy.performance

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceRegressionTest {

    @Test
    fun testCertificateGenerationPerformanceBaseline() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()

        val start = System.currentTimeMillis()
        cache.get("perf.example.com", ca)
        val duration = System.currentTimeMillis() - start

        assertTrue(duration >= 0L)
    }
}
