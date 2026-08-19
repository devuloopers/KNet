package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Unit tests verifying [ProxyRuntimeRepository] lifecycle controls.
 */
class ProxyRuntimeRepositoryTest {

    @Test
    fun testProxyIsNotRunningInitially() {
        val ca = CertificateAuthority.generate()
        val certCache = CertificateCache()
        val repo = ProxyRuntimeRepository(ca, certCache)

        assertFalse(repo.isRunning(), "Proxy server must not be running initially")
        repo.close()
    }

    @Test
    fun testStopProxyWhenNotStartedDoesNotThrow() {
        val ca = CertificateAuthority.generate()
        val certCache = CertificateCache()
        val repo = ProxyRuntimeRepository(ca, certCache)

        repo.stopProxy()
        assertFalse(repo.isRunning())
        repo.close()
    }
}
