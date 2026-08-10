package com.devuloopers.knet.engine.certificate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MtlsKeyManagerResolutionTest {

    @Test
    fun testBadsslKeyManagerResolution() {
        val certFile = listOf(
            File("sample_certs/badssl-client.p12"),
            File("../../sample_certs/badssl-client.p12"),
            File("../sample_certs/badssl-client.p12")
        ).firstOrNull { it.exists() }

        assertNotNull(certFile, "Sample certificate sample_certs/badssl-client.p12 must exist")

        val tempDir = File.createTempFile("knet-mtls-test", "").apply {
            delete()
            mkdirs()
        }

        try {
            val manager = CertificateManagerImpl(certificatesDir = tempDir)

            // Import badssl client certificate
            manager.importClientCertificate(
                path = certFile.absolutePath,
                alias = "Badssl",
                passphrase = ""
            )

            // Add mTLS wildcard rule *.badssl.com -> Badssl
            manager.addMtlsRule(
                EngineMtlsRule(
                    ruleName = "badssl",
                    hostPattern = "*.badssl.com",
                    certificateAlias = "Badssl",
                    enabled = true
                )
            )

            // Resolve KeyManagerFactory for client.badssl.com
            val kmf = manager.getKeyManagerFactory("client.badssl.com")
            assertNotNull(kmf, "KeyManagerFactory must not be null for matching host client.badssl.com")
            assertTrue(kmf.keyManagers.isNotEmpty(), "KeyManagerFactory must contain key managers")

            // Re-instantiate manager to simulate application restart
            val restartedManager = CertificateManagerImpl(certificatesDir = tempDir)
            val kmfAfterRestart = restartedManager.getKeyManagerFactory("client.badssl.com")
            assertNotNull(kmfAfterRestart, "KeyManagerFactory must survive application restart from persisted keys")
            assertTrue(kmfAfterRestart.keyManagers.isNotEmpty(), "KeyManagerFactory must contain key managers after restart")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
