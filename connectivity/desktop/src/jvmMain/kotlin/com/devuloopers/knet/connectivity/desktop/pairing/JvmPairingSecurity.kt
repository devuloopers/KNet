package com.devuloopers.knet.connectivity.desktop.pairing

import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

/** JCA implementation using CSPRNG, SHA-256, constant-time matching, and explicit proof algorithms. */
public class JvmPairingCrypto(
    private val random: SecureRandom = SecureRandom(),
) : PairingCryptography {
    override fun randomToken(entropyBytes: Int): String {
        require(entropyBytes in 16..128)
        return ByteArray(entropyBytes).also(random::nextBytes).urlEncode()
    }

    override fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).urlEncode()

    override fun constantTimeMatches(value: String, expectedDigest: String): Boolean =
        MessageDigest.isEqual(digest(value).encodeToByteArray(), expectedDigest.encodeToByteArray())

    override fun verifyDeviceProof(
        algorithm: DeviceProofAlgorithm,
        publicKeyEncoded: String,
        message: String,
        signatureEncoded: String,
    ): Boolean = runCatching {
        val keyBytes = URL_BASE64.decode(publicKeyEncoded)
        val signatureBytes = URL_BASE64.decode(signatureEncoded)
        val keyAlgorithm = when (algorithm) {
            DeviceProofAlgorithm.ED25519 -> "Ed25519"
            DeviceProofAlgorithm.ECDSA_P256_SHA256 -> "EC"
        }
        val signatureAlgorithm = when (algorithm) {
            DeviceProofAlgorithm.ED25519 -> "Ed25519"
            DeviceProofAlgorithm.ECDSA_P256_SHA256 -> "SHA256withECDSA"
        }
        val publicKey = KeyFactory.getInstance(keyAlgorithm).generatePublic(X509EncodedKeySpec(keyBytes))
        Signature.getInstance(signatureAlgorithm).run {
            initVerify(publicKey)
            update(message.encodeToByteArray())
            verify(signatureBytes)
        }
    }.getOrDefault(false)

    private fun ByteArray.urlEncode(): String = URL_BASE64.encode(this)

    private companion object {
        private val URL_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}
