package com.devuloopers.knet.engine.certificate

import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Container holding a dynamically generated cryptographic [KeyPair] and its matching [X509Certificate].
 *
 * @property keyPair The public and private keys generated for this specific certificate instance.
 * @property certificate The signed X.509 certificate verifying the keys.
 */
class LeafCertificate(
    val keyPair: KeyPair,
    val certificate: X509Certificate
)

/**
 * Service singleton responsible for generating SSL/TLS certificates on the fly.
 * Signed leaf certificates represent target remote servers during Man-in-the-Middle (MITM) traffic inspection.
 */
object LeafCertificateGenerator {

    /**
     * Key algorithm used for leaf certificate key generation.
     */
    const val KEY_ALGORITHM = "RSA"

    /**
     * Signature algorithm used to sign the leaf certificate.
     */
    const val SIGNING_ALGORITHM = "SHA256withRSA"

    /**
     * Default Organization (O) for the leaf certificate.
     */
    const val DEFAULT_LEAF_ORG = "KNet Decrypted"

    /**
     * Key size in bits for the leaf key pair.
     */
    const val KEY_SIZE = 2048

    /**
     * Default validity period for the leaf certificate in days.
     */
    const val DEFAULT_VALIDITY_DAYS = 365

    private val secureRandom = SecureRandom()

    init {
        // Register BouncyCastle as a security provider if it hasn't been added yet.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Dynamically generates and signs an SSL/TLS leaf server certificate for a target hostname.
     * The certificate is signed using the private key of the provided [CertificateAuthority].
     *
     * @param hostname The target hostname (e.g., "google.com", "*.github.com", "192.168.1.1").
     * @param ca The Certificate Authority instance whose keys will be used to sign this leaf.
     * @param validityDays The number of days the generated certificate is valid (default: [DEFAULT_VALIDITY_DAYS] days).
     * @return A signed [LeafCertificate] bundle.
     */
    fun generate(
        hostname: String,
        ca: CertificateAuthority,
        validityDays: Int = DEFAULT_VALIDITY_DAYS
    ): LeafCertificate {
        // Generate a 2048-bit RSA KeyPair for the leaf (fast generation and handshake execution)
        val keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
        keyGen.initialize(KEY_SIZE)
        val leafKeyPair = keyGen.generateKeyPair()

        val issuerDN = X500Name.getInstance(ca.certificate.subjectX500Principal.encoded)
        val subjectDN = X500Name("CN=$hostname, O=$DEFAULT_LEAF_ORG, C=US")

        val serial = BigInteger(64, secureRandom)
        // Set notBefore back by 1 day to offset client-side clock desync problems.
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60L * 60L * 24L)
        val notAfter = Date(System.currentTimeMillis() + validityDays * 24L * 60L * 60L * 1000L)

        val certBuilder = JcaX509v3CertificateBuilder(
            issuerDN,
            serial,
            notBefore,
            notAfter,
            subjectDN,
            leafKeyPair.public
        )

        // Leaf constraint: must explicitly NOT be a CA.
        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(false)
        )

        // Key usage: Digital Signature & Key Encipherment are required for server TLS key exchanges.
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )

        // Extended Key Usage: Must mark this certificate as valid for Server Authentication.
        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth))
        )

        // Subject Alternative Name (SAN) is CRITICAL. Modern HTTPS clients reject certificates without it.
        val sanType = if (isIpAddress(hostname)) GeneralName.iPAddress else GeneralName.dNSName
        val subjectAltNames = GeneralNames(GeneralName(sanType, hostname))
        certBuilder.addExtension(
            Extension.subjectAlternativeName,
            false,
            subjectAltNames
        )

        // Authority Key Identifier (SKI link referencing CA public key identifier).
        val digestCalculator = JcaDigestCalculatorProviderBuilder()
            .build()
            .get(AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1))
        val authKeyId = JcaX509ExtensionUtils(digestCalculator)
            .createAuthorityKeyIdentifier(ca.certificate)
        certBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            authKeyId
        )

        // Sign the certificate using the Root CA's private key
        val signer = JcaContentSignerBuilder(SIGNING_ALGORITHM)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(ca.privateKey)

        val holder = certBuilder.build(signer)
        val leafCertificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(holder)

        return LeafCertificate(leafKeyPair, leafCertificate)
    }

    /**
     * Determines whether the given host name is formatted as an IP address.
     *
     * @param host The hostname string.
     * @return True if the string represents an IPv4 or IPv6 address.
     */
    private fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("^[0-9.]+$")) || host.contains(":")
    }
}
