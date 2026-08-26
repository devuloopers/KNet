package com.devuloopers.knet.data.desktop.certificate

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import java.security.KeyStore
import java.security.MessageDigest
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory

/** Owned TLS material for the desktop companion certificate-readiness listener. */
class DesktopCompanionTlsIdentity private constructor(
    /** Socket factory containing a KNet-CA-signed server identity. */
    val serverSocketFactory: SSLServerSocketFactory,
    rootCertificate: ByteArray,
    /** Lowercase SHA-256 fingerprint of the KNet root certificate. */
    val rootCertificateSha256: String,
    /** Stable pairing pin; currently the persistent KNet root carried in the served certificate chain. */
    val transportIdentitySha256: String,
) {
    private val rootContent: ByteArray = rootCertificate.copyOf()

    /** Returns an owned copy of the public KNet root certificate in DER encoding. */
    fun copyRootCertificate(): ByteArray = rootContent.copyOf()

    companion object {
        /** Creates a server identity for [serverName] signed by the persistent KNet root. */
        fun create(
            serverName: String,
            certificateAuthority: CertificateAuthority,
        ): DesktopCompanionTlsIdentity {
            require(serverName.isNotBlank())
            val leaf = LeafCertificateGenerator.generate(serverName, certificateAuthority)
            val password = CharArray(0)
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, password)
                setKeyEntry(
                    "knet-companion",
                    leaf.keyPair.private,
                    password,
                    arrayOf(leaf.certificate, certificateAuthority.certificate),
                )
            }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }
            val context = SSLContext.getInstance("TLS").apply {
                init(keyManagers.keyManagers, null, null)
            }
            val root = certificateAuthority.certificate.encoded
            val rootFingerprint = root.sha256()
            return DesktopCompanionTlsIdentity(
                serverSocketFactory = context.serverSocketFactory,
                rootCertificate = root,
                rootCertificateSha256 = rootFingerprint,
                transportIdentitySha256 = rootFingerprint,
            )
        }
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte -> "%02x".format(byte) }
