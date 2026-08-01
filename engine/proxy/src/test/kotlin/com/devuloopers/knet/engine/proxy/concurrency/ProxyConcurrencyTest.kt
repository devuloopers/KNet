package com.devuloopers.knet.engine.proxy.concurrency

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProxyConcurrencyTest {

    @Test
    fun testConcurrentCertificateCacheAccessThreadSafety() = runBlocking {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()

        val hosts = listOf(
            "google.com", "github.com", "httpbin.org", "example.com",
            "openai.com", "microsoft.com", "apple.com", "kotlinlang.org"
        )

        // Dispatch 50 concurrent tasks requesting certificates across multi-threads
        val deferreds = (1..50).map { index ->
            async {
                val targetHost = hosts[index % hosts.size]
                cache.get(targetHost, ca)
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(50, results.size)
        results.forEach { cert ->
            assertNotNull(cert)
            assertNotNull(cert.certificate)
            assertNotNull(cert.keyPair)
        }
    }

    @Test
    fun testConcurrentProxyServerInitializationSafety() = runBlocking {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()
        val server = KNetProxyServer(port = 19088, ca = ca, certCache = cache)

        server.start()

        val deferreds = (1..10).map {
            async {
                server.isRunning()
            }
        }

        val statusList = deferreds.awaitAll()
        assertEquals(10, statusList.size)
        statusList.forEach { isRunning ->
            assertEquals(true, isRunning)
        }

        server.stop()
        assertEquals(false, server.isRunning())
    }
}
