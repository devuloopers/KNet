package com.devuloopers.knet.engine.certificate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
            var persistedConfiguration = CertificateConfiguration()
            val configurationStore = object : CertificateConfigurationStore {
                override fun load(): CertificateConfiguration = persistedConfiguration

                override fun persist(configuration: CertificateConfiguration) {
                    persistedConfiguration = configuration
                }
            }
            val manager = CertificateManagerImpl(
                identityDirectory = tempDir,
                configurationStore = configurationStore,
            )

            // Import badssl client certificate
            manager.importClientCertificate(
                path = certFile.absolutePath,
                alias = "Badssl",
                passphrase = "badssl.com"
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
            assertNull(manager.getKeyManagerFactory("badssl.com"), "A wildcard rule must not match its apex host")

            // Re-instantiate manager to simulate application restart
            val orphanedIdentity = tempDir.resolve("keys/orphaned.p12").apply { writeText("orphan") }
            val restartedManager = CertificateManagerImpl(
                identityDirectory = tempDir,
                configurationStore = configurationStore,
            )
            assertFalse(orphanedIdentity.exists(), "Restart must clean unreferenced internal identity material")
            val kmfAfterRestart = restartedManager.getKeyManagerFactory("client.badssl.com")
            assertNotNull(kmfAfterRestart, "KeyManagerFactory must survive application restart from persisted keys")
            assertTrue(kmfAfterRestart.keyManagers.isNotEmpty(), "KeyManagerFactory must contain key managers after restart")

            val storedIdentity = File(restartedManager.getClientCertificates().single().filePath)
            assertTrue(storedIdentity.isFile)
            restartedManager.deleteClientCertificate("Badssl")
            assertFalse(storedIdentity.exists(), "Deleting an identity must remove its normalized private-key file")
            assertTrue(restartedManager.getMtlsRules().isEmpty(), "Dependent mTLS rules must be deleted atomically")
            assertNull(restartedManager.getKeyManagerFactory("client.badssl.com"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
