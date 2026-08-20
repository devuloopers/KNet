package com.devuloopers.knet.data.desktop.certificate

import com.devuloopers.knet.engine.certificate.CertificateConfiguration
import com.devuloopers.knet.engine.certificate.EngineClientCertificate
import com.devuloopers.knet.engine.certificate.EngineMtlsRule
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopCertificateConfigurationStoreTest {

    @Test
    fun `complete certificate configuration survives a disk round trip`() {
        val directory = Files.createTempDirectory("knet-certificate-store-").toFile()
        try {
            val file = directory.resolve("certificate_configuration.json")
            val store = DesktopCertificateConfigurationStore(file)
            val expected = CertificateConfiguration(
                clientCertificates = listOf(
                    EngineClientCertificate(
                        alias = "bank-api",
                        subject = "CN=bank-api",
                        host = "client.bank.example",
                        expiration = "2030-01-01 00:00:00",
                        filePath = directory.resolve("keys/identity.p12").absolutePath,
                    )
                ),
                mtlsRules = listOf(
                    EngineMtlsRule("Bank API", "*.bank.example", "bank-api")
                ),
            )

            store.persist(expected)

            assertEquals(expected, DesktopCertificateConfigurationStore(file).load())
            assertTrue(file.readText().contains("\"version\": 1"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `corrupt configuration is reported and not treated as empty`() {
        val directory = Files.createTempDirectory("knet-certificate-store-").toFile()
        try {
            val file = directory.resolve("certificate_configuration.json").apply {
                writeText("not-json")
            }

            assertFailsWith<IllegalStateException> {
                DesktopCertificateConfigurationStore(file).load()
            }
            assertEquals("not-json", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
