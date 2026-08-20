package com.devuloopers.knet.engine.certificate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit test suite for [CertificateManagerImpl], verifying strict validation and error handling on certificate imports.
 */
class CertificateManagerImplTest {

    private val manager = CertificateManagerImpl()

    /**
     * Verifies that attempting to import a client certificate from a non-existent file path throws an [IllegalArgumentException].
     */
    @Test
    fun testImportNonExistentFileThrowsException() {
        val nonExistentPath = "/invalid/path/to/missing_cert.p12"
        val exception = assertFailsWith<IllegalArgumentException> {
            manager.importClientCertificate(path = nonExistentPath, alias = "test-alias")
        }
        assertTrue(exception.message?.contains("does not exist") == true)
        assertTrue(manager.getClientCertificates().none { it.alias == "test-alias" })
    }

    /**
     * Verifies that attempting to import an invalid/corrupted certificate file throws an [IllegalArgumentException].
     */
    @Test
    fun testImportCorruptedFileThrowsException() {
        val tempFile = File.createTempFile("corrupted_cert", ".p12").apply {
            writeText("This is not a valid PKCS12 or PEM certificate file")
            deleteOnExit()
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            manager.importClientCertificate(path = tempFile.absolutePath, alias = "corrupted-alias")
        }
        assertTrue(exception.message?.contains("Unable to import client identity") == true)
        assertTrue(manager.getClientCertificates().none { it.alias == "corrupted-alias" })
    }

    /**
     * Verifies that importing a valid PEM certificate file creates a legitimate [EngineClientCertificate].
     */
    @Test
    fun testImportValidPemCertificate() {
        val ca = CertificateAuthority.generate(commonName = "Test Client Cert")
        val (certPem, keyPem) = ca.saveToPemStrings()

        val tempPemFile = File.createTempFile("valid_cert", ".crt").apply {
            writeText(certPem + keyPem)
            deleteOnExit()
        }

        manager.importClientCertificate(path = tempPemFile.absolutePath, alias = "valid-alias")

        val certs = manager.getClientCertificates()
        assertEquals(1, certs.size)
        val imported = certs.first()
        assertEquals("valid-alias", imported.alias)
        assertTrue(imported.subject.contains("Test Client Cert"))
        assertEquals("PEM", imported.format)
    }

    @Test
    fun `certificate-only PEM is rejected because it cannot authenticate mTLS`() {
        val ca = CertificateAuthority.generate(commonName = "Certificate Without Key")
        val certificatePem = ca.saveToPemStrings().first
        val certificateFile = File.createTempFile("certificate_only", ".pem").apply {
            writeText(certificatePem)
            deleteOnExit()
        }

        assertFailsWith<IllegalArgumentException> {
            manager.importClientCertificate(certificateFile.absolutePath, "certificate-only")
        }
        assertTrue(manager.getClientCertificates().none { it.alias == "certificate-only" })
    }

    @Test
    fun `missing persisted private key is disabled and repaired in the stored snapshot`() {
        val directory = kotlin.io.path.createTempDirectory("missing-client-key-").toFile()
        var persisted = CertificateConfiguration(
            clientCertificates = listOf(
                EngineClientCertificate(
                    alias = "missing-key",
                    subject = "CN=missing-key",
                    host = "missing.example",
                    expiration = "2030-01-01 00:00:00",
                    enabled = true,
                    filePath = directory.resolve("keys/missing.p12").absolutePath,
                )
            )
        )
        val store = object : CertificateConfigurationStore {
            override fun load(): CertificateConfiguration = persisted

            override fun persist(configuration: CertificateConfiguration) {
                persisted = configuration
            }
        }

        try {
            val restored = CertificateManagerImpl(
                identityDirectory = directory,
                configurationStore = store,
            )

            assertFalse(restored.getClientCertificates().single().enabled)
            assertFalse(persisted.clientCertificates.single().enabled)
        } finally {
            directory.deleteRecursively()
        }
    }
}
