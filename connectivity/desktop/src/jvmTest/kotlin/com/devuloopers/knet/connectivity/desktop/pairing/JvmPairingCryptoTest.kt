package com.devuloopers.knet.connectivity.desktop.pairing

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingInvitationId
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmPairingCryptoTest {
    @Test
    fun verifiesAndroidCompatibleP256ProofAndRejectsTampering() {
        val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val request = PairingCompletionRequest(
            invitationId = PairingInvitationId("invitation-1"),
            invitationSecret = "one-time-secret!",
            deviceId = RegisteredDeviceId("device-1"),
            displayName = "Pixel",
            publicKeyEncoded = keyPair.public.encoded.urlEncode(),
            proofSignatureEncoded = "pending",
            proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(request.proofMessage().encodeToByteArray())
            sign().urlEncode()
        }
        val crypto = JvmPairingCrypto()

        assertTrue(
            crypto.verifyDeviceProof(
                request.proofAlgorithm,
                request.publicKeyEncoded,
                request.proofMessage(),
                signature,
            ),
        )
        assertFalse(
            crypto.verifyDeviceProof(
                request.proofAlgorithm,
                request.publicKeyEncoded,
                request.proofMessage() + "-tampered",
                signature,
            ),
        )
        assertFalse(
            crypto.verifyDeviceProof(
                DeviceProofAlgorithm.ED25519,
                request.publicKeyEncoded,
                request.proofMessage(),
                signature,
            ),
        )
    }

    private fun ByteArray.urlEncode(): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).encode(this)
}
