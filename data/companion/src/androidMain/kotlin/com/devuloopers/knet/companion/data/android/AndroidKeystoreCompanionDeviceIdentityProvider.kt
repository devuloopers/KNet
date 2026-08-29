package com.devuloopers.knet.companion.data.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.data.crypto.CompanionSha256
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Persistent P-256 proof identity backed by a non-exportable Android Keystore private key.
 *
 * @param ioDispatcher worker dispatcher used for Android Keystore and key-generation operations.
 */
public class AndroidKeystoreCompanionDeviceIdentityProvider(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CompanionDeviceIdentityProvider {
    private val blockingCalls: AndroidBlockingCallExecutor = AndroidBlockingCallExecutor(ioDispatcher)

    override suspend fun getOrCreate(): CompanionDeviceIdentity = blockingCalls.execute {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(DEVICE_KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER).run {
                initialize(
                    KeyGenParameterSpec.Builder(
                        DEVICE_KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
                generateKeyPair()
            }
        }
        val publicKey = checkNotNull(loadKeyStore().getCertificate(DEVICE_KEY_ALIAS)?.publicKey) {
            "Android Keystore did not return the companion public key."
        }
        val publicKeyEncoded = URL_SAFE_BASE64.encode(publicKey.encoded)
        val stableId = CompanionSha256.digest(publicKey.encoded).copyOf(18)
        CompanionDeviceIdentity(
            deviceId = RegisteredDeviceId(
                URL_SAFE_BASE64.encode(stableId),
            ),
            proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
            publicKeyEncoded = publicKeyEncoded,
            privateKeyReference = DEVICE_KEY_ALIAS,
        )
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private companion object {
        val URL_SAFE_BASE64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        const val KEYSTORE_PROVIDER: String = "AndroidKeyStore"
        const val DEVICE_KEY_ALIAS: String = "knet.companion.device-proof.v1"
    }
}
