package com.devuloopers.knet.engine.certificate

import com.devuloopers.knet.engine.certificate.ssl.KNetTrustManagerProvider
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.cert.CertificateException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests owner-only certificate storage policy and opaque alias filenames. */
class CertificateFileSecurityTest {

    /** Verifies user aliases cannot become directory traversal segments. */
    @Test
    fun `identity filename does not embed alias path text`() {
        val fileName = CertificateFileSecurity.opaqueIdentityFileName("../../client identity", "p12")

        assertFalse(fileName.contains("client"))
        assertFalse(fileName.contains('/'))
        assertFalse(fileName.contains(".."))
        assertEquals(64, fileName.substringBefore('.').length)
        assertTrue(fileName.endsWith(".p12"))
    }

    /** Verifies a readable export name cannot contain user-controlled directory segments. */
    @Test
    fun `export filename remains within selected directory`() {
        val fileName = CertificateFileSecurity.exportIdentityFileName("../../client identity", "P12")

        assertEquals("client_identity.p12", fileName)
        assertFalse(fileName.contains('/'))
        assertFalse(fileName.contains(".."))
    }

    /** Verifies strict mode rejects an untrusted generated authority while explicit insecure mode accepts it. */
    @Test
    fun `strict trust manager does not fall back to trust all`() {
        val untrustedCertificate = CertificateAuthority.generate().certificate
        KNetTrustManagerProvider.invalidateCache()

        assertFailsWith<CertificateException> {
            KNetTrustManagerProvider.getX509TrustManager(verifySsl = true)
                .checkServerTrusted(arrayOf(untrustedCertificate), "RSA")
        }
        KNetTrustManagerProvider.getX509TrustManager(verifySsl = false)
            .checkServerTrusted(arrayOf(untrustedCertificate), "RSA")
    }

    /** Verifies POSIX-capable filesystems receive owner read-write permissions only. */
    @Test
    fun `secret files are owner only when posix permissions are available`() {
        val directory = Files.createTempDirectory("knet-certificate-security-").toFile()
        val secret = directory.resolve("secret.key").apply { writeText("secret") }

        CertificateFileSecurity.secureDirectory(directory)
        CertificateFileSecurity.secureSecretFile(secret)
        val permissions = runCatching { Files.getPosixFilePermissions(secret.toPath()) }.getOrNull()

        if (permissions != null) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                permissions,
            )
        }

        secret.delete()
        directory.delete()
    }
}
