package com.devuloopers.knet.engine.certificate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CertificateStoreTest {

    // -------------------------------------------------------------------------
    // Client Certificate Matrix Tests
    // -------------------------------------------------------------------------

    @Test
    fun `client certs - non existent file returns empty list`() {
        val nonExistent = File("target/non_existent_certs_${System.currentTimeMillis()}.json")
        val store = CertificateStore(nonExistent)
        assertTrue(store.loadClientCertificates().isEmpty())
    }

    @Test
    fun `client certs - null file returns empty list`() {
        val store = CertificateStore(null)
        assertTrue(store.loadClientCertificates().isEmpty())
    }

    @Test
    fun `client certs - empty or blank file returns empty list`() {
        val temp = File.createTempFile("empty_certs", ".json").apply { deleteOnExit() }
        temp.writeText("   \n  ")
        val store = CertificateStore(temp)
        assertTrue(store.loadClientCertificates().isEmpty())
    }

    @Test
    fun `client certs - valid json decoding`() {
        val temp = File.createTempFile("valid_certs", ".json").apply { deleteOnExit() }
        val sample = EngineClientCertificate(alias = "dev_cert", subject = "CN=Dev", host = "dev.local", expiration = "2030-01-01")
        val jsonContent = CertificateSerializer.encodeClientCertificates(listOf(sample))
        temp.writeText(jsonContent)

        val store = CertificateStore(temp)
        val loaded = store.loadClientCertificates()
        assertEquals(1, loaded.size)
        assertEquals("dev_cert", loaded[0].alias)
        assertEquals("dev.local", loaded[0].host)
    }

    @Test
    fun `client certs - json with unknown fields is ignored`() {
        val temp = File.createTempFile("unknown_fields_cert", ".json").apply { deleteOnExit() }
        val rawJson = """
            [
              {
                "alias": "cert1",
                "subject": "CN=Test",
                "host": "test.com",
                "expiration": "2030",
                "futureUnknownField": "someValue",
                "anotherNewSetting": 42
              }
            ]
        """.trimIndent()
        temp.writeText(rawJson)

        val store = CertificateStore(temp)
        val loaded = store.loadClientCertificates()
        assertEquals(1, loaded.size)
        assertEquals("cert1", loaded[0].alias)
    }

    @Test
    fun `client certs - invalid json and invalid legacy leaves original file untouched`() {
        val temp = File.createTempFile("corrupt_certs", ".json").apply { deleteOnExit() }
        val corruptContent = ":::NOT_JSON_AND_NOT_PIPE_LEGACY:::"
        temp.writeText(corruptContent)

        val store = CertificateStore(temp)
        val loaded = store.loadClientCertificates()
        assertTrue(loaded.isEmpty())

        // Critical safety check: original file must NOT be overwritten with empty list []
        assertEquals(corruptContent, temp.readText())
    }

    @Test
    fun `client certs - valid legacy format parsing and auto migration`() {
        val temp = File.createTempFile("legacy_certs", ".json").apply { deleteOnExit() }
        val legacyText = "Badssl|CN=BadSSL Client Certificate|*.badssl.com|2027-12-31|true|PKCS12|365|CN=BadSSL Client Certificate|CN=BadSSL CA|12345|san.badssl.com|RSA 2048-bit|ABCDEF|/path/to/cert.p12"
        temp.writeText(legacyText)

        val store = CertificateStore(temp)
        val loaded = store.loadClientCertificates()
        assertEquals(1, loaded.size)
        assertEquals("Badssl", loaded[0].alias)
        assertEquals("*.badssl.com", loaded[0].host)

        // Verify file was migrated to valid JSON
        val migratedContent = temp.readText()
        assertTrue(migratedContent.startsWith("["))
    }

    @Test
    fun `client certs - legacy format with optional fields missing uses defaults`() {
        val temp = File.createTempFile("legacy_short", ".json").apply { deleteOnExit() }
        temp.writeText("min_cert|CN=Min|min.local|2029-01-01")

        val store = CertificateStore(temp)
        val loaded = store.loadClientCertificates()
        assertEquals(1, loaded.size)
        assertEquals("min_cert", loaded[0].alias)
        assertEquals("PKCS12", loaded[0].format)
        assertEquals(365, loaded[0].daysUntilExpiration)
        assertTrue(loaded[0].enabled)
    }

    @Test
    fun `client certs - multiple legacy records`() {
        val temp = File.createTempFile("legacy_multi", ".json").apply { deleteOnExit() }
        val multiLegacy = """
            c1|CN=Cert1|c1.com|2028-01-01|true
            c2|CN=Cert2|c2.com|2028-02-02|false
        """.trimIndent()
        temp.writeText(multiLegacy)

        val store = CertificateStore(temp)
        val loaded = store.loadClientCertificates()
        assertEquals(2, loaded.size)
        assertEquals("c1", loaded[0].alias)
        assertEquals("c2", loaded[1].alias)
        assertFalse(loaded[1].enabled)
    }

    @Test
    fun `client certs - persistence creates parent directories`() {
        val parentDir = File(System.getProperty("java.io.tmpdir"), "knet_test_dir_${System.currentTimeMillis()}")
        val targetFile = File(parentDir, "nested_certs.json")
        parentDir.deleteOnExit()
        targetFile.deleteOnExit()

        val store = CertificateStore(targetFile)
        val cert = EngineClientCertificate(alias = "nested", subject = "CN=Nested", host = "nested.io", expiration = "2031")
        store.persistClientCertificates(listOf(cert))

        assertTrue(targetFile.exists())
        val reloaded = store.loadClientCertificates()
        assertEquals(1, reloaded.size)
        assertEquals("nested", reloaded[0].alias)
    }

    // -------------------------------------------------------------------------
    // mTLS Rules Matrix Tests
    // -------------------------------------------------------------------------

    @Test
    fun `mtls rules - non existent or empty file returns empty list`() {
        val nonExistent = File("target/non_existent_rules_${System.currentTimeMillis()}.json")
        val store = CertificateStore(nonExistent)
        assertTrue(store.loadMtlsRules().isEmpty())

        val temp = File.createTempFile("empty_rules", ".json").apply { deleteOnExit() }
        temp.writeText("")
        val store2 = CertificateStore(temp)
        assertTrue(store2.loadMtlsRules().isEmpty())
    }

    @Test
    fun `mtls rules - valid json and json with unknown fields`() {
        val temp = File.createTempFile("valid_rules", ".json").apply { deleteOnExit() }
        val rawJson = """
            [
              {
                "ruleName": "rule1",
                "hostPattern": "*.api.com",
                "certificateAlias": "api_cert",
                "enabled": true,
                "extraField": "ignored"
              }
            ]
        """.trimIndent()
        temp.writeText(rawJson)

        val store = CertificateStore(temp)
        val loaded = store.loadMtlsRules()
        assertEquals(1, loaded.size)
        assertEquals("rule1", loaded[0].ruleName)
        assertEquals("*.api.com", loaded[0].hostPattern)
    }

    @Test
    fun `mtls rules - invalid data leaves original file untouched`() {
        val temp = File.createTempFile("invalid_rules", ".json").apply { deleteOnExit() }
        val invalidText = "INVALID_RULE_FORMAT_NO_PIPES_NO_JSON"
        temp.writeText(invalidText)

        val store = CertificateStore(temp)
        val loaded = store.loadMtlsRules()
        assertTrue(loaded.isEmpty())

        // File must not be overwritten
        assertEquals(invalidText, temp.readText())
    }

    @Test
    fun `mtls rules - legacy format migration`() {
        val temp = File.createTempFile("legacy_rules", ".json").apply { deleteOnExit() }
        val legacyText = "badssl_rule|*.badssl.com|BadsslAlias|true"
        temp.writeText(legacyText)

        val store = CertificateStore(temp)
        val loaded = store.loadMtlsRules()
        assertEquals(1, loaded.size)
        assertEquals("badssl_rule", loaded[0].ruleName)
        assertEquals("*.badssl.com", loaded[0].hostPattern)
        assertEquals("BadsslAlias", loaded[0].certificateAlias)
        assertTrue(loaded[0].enabled)

        // Verify file was migrated to JSON
        assertTrue(temp.readText().startsWith("["))
    }

    // -------------------------------------------------------------------------
    // Round-Trip Tests (Section 18)
    // -------------------------------------------------------------------------

    @Test
    fun `roundtrip - EngineClientCertificate objects to json to store to load`() {
        val temp = File.createTempFile("roundtrip_certs", ".json").apply { deleteOnExit() }
        val original = listOf(
            EngineClientCertificate(
                alias = "cert_a",
                subject = "CN=CertA",
                host = "a.com",
                expiration = "2032-01-01",
                enabled = true,
                format = "PEM",
                daysUntilExpiration = 120,
                subjectDn = "CN=CertA, O=Org",
                issuerDn = "CN=RootCA",
                serialNumber = "00A1B2C3",
                sanList = listOf("a.com", "sub.a.com"),
                publicKeyAlgorithm = "RSA 4096-bit",
                sha256Fingerprint = "11223344556677889900",
                filePath = "/etc/certs/cert_a.pem"
            )
        )

        val store = CertificateStore(temp)
        store.persistClientCertificates(original)

        val loaded = store.loadClientCertificates()
        assertEquals(original, loaded)
    }

    @Test
    fun `roundtrip - EngineMtlsRule objects to json to store to load`() {
        val temp = File.createTempFile("roundtrip_rules", ".json").apply { deleteOnExit() }
        val original = listOf(
            EngineMtlsRule(
                ruleName = "rule_internal",
                hostPattern = "*.internal.net",
                certificateAlias = "corp_cert",
                enabled = true
            ),
            EngineMtlsRule(
                ruleName = "rule_staging",
                hostPattern = "stg.internal.net",
                certificateAlias = "stg_cert",
                enabled = false
            )
        )

        val store = CertificateStore(temp)
        store.persistMtlsRules(original)

        val loaded = store.loadMtlsRules()
        assertEquals(original, loaded)
    }

    // -------------------------------------------------------------------------
    // Full Migration Lifecycle Test (Section 19)
    // -------------------------------------------------------------------------

    @Test
    fun `migration lifecycle - legacy file to load to json to second load without re-migration`() {
        val temp = File.createTempFile("migration_lifecycle", ".json").apply { deleteOnExit() }
        temp.writeText("legacy_mtls|*.corp.com|CorpCert|true")

        val store = CertificateStore(temp)

        // 1st load: parses legacy pipe format and persists JSON
        val firstLoad = store.loadMtlsRules()
        assertEquals(1, firstLoad.size)
        assertEquals("legacy_mtls", firstLoad[0].ruleName)

        val jsonOnDisk = temp.readText()
        assertTrue(jsonOnDisk.startsWith("["))

        // 2nd load: parses JSON directly from disk (second load does not execute migration)
        val secondLoad = store.loadMtlsRules()
        assertEquals(firstLoad, secondLoad)
    }
}
