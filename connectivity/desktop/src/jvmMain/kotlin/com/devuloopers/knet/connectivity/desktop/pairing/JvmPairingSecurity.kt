package com.devuloopers.knet.connectivity.desktop.pairing

import com.devuloopers.knet.application.port.pairing.PairingCryptoPort
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

/** JCA implementation using CSPRNG, SHA-256, constant-time digest matching, and Ed25519 proof. */
public class JvmPairingCrypto(
    private val random: SecureRandom = SecureRandom(),
) : PairingCryptoPort {
    override fun randomToken(entropyBytes: Int): String {
        require(entropyBytes in 16..128)
        return ByteArray(entropyBytes).also(random::nextBytes).urlEncode()
    }

    override fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).urlEncode()

    override fun constantTimeMatches(value: String, expectedDigest: String): Boolean =
        MessageDigest.isEqual(digest(value).encodeToByteArray(), expectedDigest.encodeToByteArray())

    override fun verifyDeviceProof(
        publicKeyEncoded: String,
        message: String,
        signatureEncoded: String,
    ): Boolean = runCatching {
        val keyBytes = URL_BASE64.decode(publicKeyEncoded)
        val signatureBytes = URL_BASE64.decode(signatureEncoded)
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
        Signature.getInstance("Ed25519").run {
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
