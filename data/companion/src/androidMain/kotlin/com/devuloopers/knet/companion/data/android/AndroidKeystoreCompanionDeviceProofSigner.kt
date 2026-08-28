package com.devuloopers.knet.companion.data.android

import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import java.security.KeyStore
import java.security.Signature
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Signs pairing transcripts without exporting the companion private key.
 *
 * @param ioDispatcher worker dispatcher used for Android Keystore and signature operations.
 */
public class AndroidKeystoreCompanionDeviceProofSigner(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CompanionDeviceProofSigner {
    private val blockingCalls: AndroidBlockingCallExecutor = AndroidBlockingCallExecutor(ioDispatcher)

    override suspend fun sign(identity: CompanionDeviceIdentity, message: String): String {
        require(identity.proofAlgorithm == DeviceProofAlgorithm.ECDSA_P256_SHA256) {
            "Android proof signer received an unsupported algorithm."
        }
        return blockingCalls.execute {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val privateKey = checkNotNull(keyStore.getKey(identity.privateKeyReference, null)) {
                "Companion proof key is unavailable."
            }
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey as java.security.PrivateKey)
                update(message.encodeToByteArray())
                sign()
            }
            URL_SAFE_BASE64.encode(signature)
        }
    }

    private companion object {
        val URL_SAFE_BASE64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        const val KEYSTORE_PROVIDER: String = "AndroidKeyStore"
    }
}
