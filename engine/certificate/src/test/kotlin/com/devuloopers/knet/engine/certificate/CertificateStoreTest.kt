package com.devuloopers.knet.engine.certificate

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertificateStoreTest {
    @Test
    fun `missing null and blank stores are empty`() {
        val root = Files.createTempDirectory("knet-certificate-empty-").toFile()
        try {
            assertTrue(CertificateStore(null).loadClientCertificates().isEmpty())
            assertTrue(CertificateStore(root.resolve("missing.json")).loadClientCertificates().isEmpty())
            val blank = root.resolve("blank.json").apply { writeText("  \n") }
            assertTrue(CertificateStore(blank).loadMtlsRules().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `client certificates round trip through json`() {
        withTemporaryStore { store, _ ->
            val certificates = listOf(
                EngineClientCertificate(
                    alias = "client-a",
                    subject = "CN=Client A",
                    host = "api.example.test",
                    expiration = "2032-01-01",
                    sanList = listOf("api.example.test"),
                )
            )

            store.persistClientCertificates(certificates)

            assertEquals(certificates, store.loadClientCertificates())
        }
    }

    @Test
    fun `mtls rules round trip through json`() {
        withTemporaryStore { store, _ ->
            val rules = listOf(EngineMtlsRule("internal", "*.internal.test", "client-a", true))

            store.persistMtlsRules(rules)

            assertEquals(rules, store.loadMtlsRules())
        }
    }

    @Test
    fun `invalid json is rejected without overwriting it`() {
        withTemporaryStore { store, file ->
            val invalid = "not-json"
            file.writeText(invalid)

            assertTrue(store.loadClientCertificates().isEmpty())
            assertEquals(invalid, file.readText())
        }
    }

    @Test
    fun `persistence creates parent directories`() {
        val root = Files.createTempDirectory("knet-certificate-parent-").toFile()
        val file = root.resolve("nested/client-certificates.json")
        try {
            val store = CertificateStore(file)
            val certificate = EngineClientCertificate("client", "CN=Client", "example.test", "2030")

            store.persistClientCertificates(listOf(certificate))

            assertTrue(file.isFile)
            assertEquals(listOf(certificate), store.loadClientCertificates())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withTemporaryStore(block: (CertificateStore, File) -> Unit) {
        val root = Files.createTempDirectory("knet-certificate-store-").toFile()
        try {
            val file = root.resolve("store.json")
            block(CertificateStore(file), file)
        } finally {
            root.deleteRecursively()
        }
    }
}
