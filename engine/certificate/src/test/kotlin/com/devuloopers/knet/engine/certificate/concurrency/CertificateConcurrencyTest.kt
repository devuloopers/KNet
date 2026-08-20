package com.devuloopers.knet.engine.certificate.concurrency

import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.certificate.CertificateManagerImpl
import com.devuloopers.knet.engine.certificate.EngineMtlsRule
import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.engine.certificate.util.TestCertificateFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class CertificateConcurrencyTest {

    private val ca = TestCertificateFactory.createTestCa("Concurrency CA", "Concurrency Org")

    @Test
    fun testParallelLeafGeneration() {
        val threadCount = 4
        val generationsPerThread = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val generatedSerials = ConcurrentHashMap.newKeySet<String>()

        for (i in 0 until threadCount) {
            executor.submit {
                for (j in 0 until generationsPerThread) {
                    val leaf = LeafCertificateGenerator.generate("domain-$i-$j.com", ca)
                    assertNotNull(leaf.certificate)
                    generatedSerials.add(leaf.certificate.serialNumber.toString())
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(30, TimeUnit.SECONDS)
        assertEquals(true, finished, "Parallel generation should complete cleanly within timeout")
        assertEquals(threadCount * generationsPerThread, generatedSerials.size, "All generated certificates must have unique serial numbers")
    }

    @Test
    fun testConcurrentCacheAccess() {
        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val cache = CertificateCache()

        for (i in 0 until threadCount) {
            executor.submit {
                for (j in 0 until 50) {
                    val leaf = cache.get("shared-domain.com", ca)
                    assertNotNull(leaf.certificate)
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(30, TimeUnit.SECONDS)
        assertEquals(true, finished, "Concurrent cache reads should complete cleanly")
        assertEquals(1, cache.size(), "Cache must contain exactly 1 cached entry for the shared domain")
    }

    @Test
    fun `asynchronous misses for one host share the generated leaf`() {
        val executor = Executors.newFixedThreadPool(4)
        val cache = CertificateCache()
        try {
            val futures = List(16) {
                cache.getAsync("single-flight.example", ca, executor)
            }
            val leaves = futures.map { future -> future.get(30L, TimeUnit.SECONDS) }
            leaves.drop(1).forEach { leaf -> assertSame(leaves.first(), leaf) }
            assertEquals(1, cache.size())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `certificate manager publishes atomic rule snapshots during concurrent mutation`() {
        val manager = CertificateManagerImpl(ca = ca)
        val (certificatePem, privateKeyPem) = ca.saveToPemStrings()
        val identityFile = File.createTempFile("concurrent-client-identity", ".pem").apply {
            writeText(certificatePem + privateKeyPem)
        }
        manager.importClientCertificate(identityFile.absolutePath, "concurrent-client")
        val workers = 8
        val rulesPerWorker = 50
        val executor = Executors.newFixedThreadPool(workers)
        try {
            val mutations = List(workers) { worker ->
                executor.submit {
                    repeat(rulesPerWorker) { index ->
                        manager.addMtlsRule(
                            EngineMtlsRule(
                                ruleName = "rule-$worker-$index",
                                hostPattern = "service-$worker-$index.example.test",
                                certificateAlias = "concurrent-client",
                                enabled = true,
                            ),
                        )
                        manager.getMtlsRules()
                    }
                }
            }
            mutations.forEach { mutation -> mutation.get(30L, TimeUnit.SECONDS) }

            assertEquals(workers * rulesPerWorker, manager.getMtlsRules().size)
        } finally {
            executor.shutdownNow()
            identityFile.delete()
        }
    }
}
