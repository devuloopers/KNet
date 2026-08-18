package com.devuloopers.knet.engine.certificate

import com.devuloopers.knet.engine.certificate.CertificateAuthority.Companion.DEFAULT_CA_CN
import com.devuloopers.knet.engine.certificate.CertificateAuthority.Companion.DEFAULT_ORG
import com.devuloopers.knet.engine.certificate.CertificateAuthority.Companion.DEFAULT_VALIDITY_DAYS
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Represents the Certificate Authority (CA) used by KNet to intercept and decrypt HTTPS traffic.
 * This class holds the CA's private key and its self-signed X.509 certificate.
 *
 * @property privateKey The private key of the Certificate Authority, used to sign leaf certificates dynamically.
 * @property certificate The self-signed X.509 certificate of the CA.
 */
class CertificateAuthority(
    val privateKey: PrivateKey,
    val certificate: X509Certificate
) {

    companion object {
        /**
         * Key algorithm used for CA key generation.
         */
        const val KEY_ALGORITHM = "RSA"

        /**
         * Signature algorithm used to sign the CA certificate.
         */
        const val SIGNING_ALGORITHM = "SHA256withRSA"

        /**
         * Default Common Name (CN) for the Root CA.
         */
        const val DEFAULT_CA_CN = "KNet Root CA"

        /**
         * Default Organization (O) for the Root CA.
         */
        const val DEFAULT_ORG = "Devuloopers"

        /**
         * Key size in bits for the Root CA key pair.
         */
        const val KEY_SIZE = 4096

        /**
         * Default validity period for the CA certificate in days.
         */
        const val DEFAULT_VALIDITY_DAYS = 3650

        init {
            // Register BouncyCastle as a security provider if it hasn't been added yet.
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        /**
         * Generates a new self-signed Certificate Authority (CA) key pair and certificate.
         * The key pair is generated using 4096-bit RSA for cryptographic strength.
         *
         * @param commonName The Common Name (CN) for the CA certificate (default: [DEFAULT_CA_CN]).
         * @param org The Organization (O) for the CA certificate (default: [DEFAULT_ORG]).
         * @param validityDays The number of days the CA certificate will be valid (default: [DEFAULT_VALIDITY_DAYS] days / 10 years).
         * @return A newly generated [CertificateAuthority] instance.
         */
        fun generate(
            commonName: String = DEFAULT_CA_CN,
            org: String = DEFAULT_ORG,
            validityDays: Int = DEFAULT_VALIDITY_DAYS
        ): CertificateAuthority {
            val keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
            keyGen.initialize(KEY_SIZE)
            val keyPair = keyGen.generateKeyPair()

            val subjectDN = X500Name("CN=$commonName, O=$org, C=US")
            val serial = BigInteger(160, SecureRandom())
            val notBefore = Date()
            val notAfter = Date(kotlin.time.Clock.System.now().toEpochMilliseconds() + validityDays * 24L * 60L * 60L * 1000L)

            val certBuilder = JcaX509v3CertificateBuilder(
                subjectDN,
                serial,
                notBefore,
                notAfter,
                subjectDN,
                keyPair.public
            )

            // CA constraints: must set isCA = true so clients recognize this as a root certificate.
            certBuilder.addExtension(
                Extension.basicConstraints,
                true,
                BasicConstraints(true)
            )

            // Key usage: Must support Certificate signing and CRL signing.
            certBuilder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
            )

            // Subject Key Identifier (SKI) is required for dynamic signing references.
            val digestCalculator = JcaDigestCalculatorProviderBuilder()
                .build()
                .get(AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1))
            val subjectKeyId = JcaX509ExtensionUtils(digestCalculator)
                .createSubjectKeyIdentifier(keyPair.public)
            certBuilder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                subjectKeyId
            )

            val signer = JcaContentSignerBuilder(SIGNING_ALGORITHM)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.private)

            val holder = certBuilder.build(signer)
            val certificate = JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder)

            return CertificateAuthority(keyPair.private, certificate)
        }

        /**
         * Loads a Certificate Authority from PEM-formatted files on disk.
         *
         * @param certFile The file containing the X.509 certificate in PEM format.
         * @param keyFile The file containing the private key in PEM format.
         * @return A loaded [CertificateAuthority] instance.
         * @throws IllegalArgumentException if the files cannot be read or are invalid.
         */
        fun loadFromPem(certFile: File, keyFile: File): CertificateAuthority {
            return certFile.reader().use { certReader ->
                keyFile.reader().use { keyReader ->
                    loadFromPem(certReader, keyReader)
                }
            }
        }

        /**
         * Loads a Certificate Authority from readers containing PEM data.
         *
         * @param certReader The reader containing the X.509 certificate PEM string.
         * @param keyReader The reader containing the private key PEM string.
         * @return A loaded [CertificateAuthority] instance.
         * @throws IllegalArgumentException if the objects cannot be parsed.
         */
        fun loadFromPem(certReader: Reader, keyReader: Reader): CertificateAuthority {
            val certParser = PEMParser(certReader)
            val certObj = certParser.readObject() as? X509CertificateHolder
                ?: throw IllegalArgumentException("Could not read X509CertificateHolder from reader")
            val cert = JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certObj)

            val keyParser = PEMParser(keyReader)
            val keyObj = keyParser.readObject()
                ?: throw IllegalArgumentException("Could not read PrivateKey from reader")

            val converter = JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
            val privateKey = when (keyObj) {
                is PEMKeyPair -> converter.getPrivateKey(keyObj.privateKeyInfo)
                is PrivateKeyInfo -> converter.getPrivateKey(keyObj)
                else -> throw IllegalArgumentException("Unsupported private key type: ${keyObj::class.java.name}")
            }

            return CertificateAuthority(privateKey, cert)
        }

        /**
         * Loads a Certificate Authority from raw PEM string inputs.
         *
         * @param certPem The PEM representation of the CA certificate.
         * @param keyPem The PEM representation of the private key.
         * @return A loaded [CertificateAuthority] instance.
         */
        fun loadFromPemStrings(certPem: String, keyPem: String): CertificateAuthority {
            return loadFromPem(StringReader(certPem), StringReader(keyPem))
        }
    }

    /**
     * Saves the Certificate Authority's certificate and private key to disk in PEM format.
     *
     * @param certFile The destination file for the CA certificate.
     * @param keyFile The destination file for the private key.
     */
    fun saveToPem(certFile: File, keyFile: File) {
        certFile.parentFile?.let(CertificateFileSecurity::secureDirectory)
        keyFile.parentFile?.let(CertificateFileSecurity::secureDirectory)
        certFile.writer().use { certWriter ->
            keyFile.writer().use { keyWriter ->
                saveToPem(certWriter, keyWriter)
            }
        }
        CertificateFileSecurity.secureSecretFile(certFile)
        CertificateFileSecurity.secureSecretFile(keyFile)
    }

    /**
     * Writes the Certificate Authority's certificate and private key to writers in PEM format.
     *
     * @param certWriter The target writer for the certificate.
     * @param keyWriter The target writer for the private key.
     */
    fun saveToPem(certWriter: Writer, keyWriter: Writer) {
        val pemCertWriter = JcaPEMWriter(certWriter)
        pemCertWriter.writeObject(certificate)
        pemCertWriter.flush()

        val pemKeyWriter = JcaPEMWriter(keyWriter)
        pemKeyWriter.writeObject(privateKey)
        pemKeyWriter.flush()
    }

    /**
     * Serializes the Certificate Authority key and certificate into raw PEM strings.
     *
     * @return A [Pair] containing the certificate PEM string (first) and private key PEM string (second).
     */
    fun saveToPemStrings(): Pair<String, String> {
        val certWriter = StringWriter()
        val keyWriter = StringWriter()
        saveToPem(certWriter, keyWriter)
        return Pair(certWriter.toString(), keyWriter.toString())
    }
}
