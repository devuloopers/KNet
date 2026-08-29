@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.data.ios

import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.data.crypto.CompanionSha256
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import kotlin.io.encoding.Base64
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyIsAlgorithmSupported
import platform.Security.SecKeyRef
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecKeyOperationTypeSign
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef

/**
 * Persistent P-256 proof identity backed by an iOS Keychain non-exportable private key.
 *
 * Security.framework exposes a P-256 public key as an ANSI X9.63 point. KNet wraps that point in the standard
 * SubjectPublicKeyInfo envelope before pairing so the desktop can verify it with the same X.509 key decoder used
 * for Android.
 */
public class IosKeychainCompanionDeviceIdentity : CompanionDeviceIdentityProvider, CompanionDeviceProofSigner {
    override suspend fun getOrCreate(): CompanionDeviceIdentity {
        val privateKey = loadPrivateKey() ?: createPrivateKey()
        return try {
            val publicKey = checkNotNull(SecKeyCopyPublicKey(privateKey)) {
                "iOS Keychain did not return the companion public key."
            }
            try {
                val externalPoint = publicKey.externalRepresentation()
                val subjectPublicKeyInfo = externalPoint.asP256SubjectPublicKeyInfo()
                CompanionDeviceIdentity(
                    deviceId = RegisteredDeviceId(
                        URL_SAFE_BASE64.encode(CompanionSha256.digest(subjectPublicKeyInfo).copyOf(DEVICE_ID_BYTES)),
                    ),
                    proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
                    publicKeyEncoded = URL_SAFE_BASE64.encode(subjectPublicKeyInfo),
                    privateKeyReference = DEVICE_KEY_REFERENCE,
                )
            } finally {
                CFRelease(publicKey)
            }
        } finally {
            CFRelease(privateKey)
        }
    }

    override suspend fun sign(identity: CompanionDeviceIdentity, message: String): String {
        require(identity.proofAlgorithm == DeviceProofAlgorithm.ECDSA_P256_SHA256) {
            "iOS proof signer received an unsupported algorithm."
        }
        require(identity.privateKeyReference == DEVICE_KEY_REFERENCE) {
            "iOS proof signer received an unknown key reference."
        }
        val privateKey = checkNotNull(loadPrivateKey()) { "The iOS companion proof key is unavailable." }
        return try {
            check(
                SecKeyIsAlgorithmSupported(
                    privateKey,
                    kSecKeyOperationTypeSign,
                    kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
                ),
            ) { "The iOS companion proof key cannot create the required signature." }
            val messageData = message.encodeToByteArray().toCfData()
            try {
                val signature = checkNotNull(
                    SecKeyCreateSignature(
                        privateKey,
                        kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
                        messageData,
                        null,
                    ),
                ) { "iOS Security.framework could not sign the companion pairing transcript." }
                try {
                    URL_SAFE_BASE64.encode(signature.copyBytes())
                } finally {
                    CFRelease(signature)
                }
            } finally {
                CFRelease(messageData)
            }
        } finally {
            CFRelease(privateKey)
        }
    }

    private fun loadPrivateKey(): SecKeyRef? = withKeyQuery { query ->
        CFDictionarySetValue(query, kSecReturnRef, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<COpaquePointerVar>()
            result.value = null
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecItemNotFound -> null
                errSecSuccess -> checkNotNull(result.value).reinterpret()
                else -> error("Unable to read the companion proof key from iOS Keychain (status=$status).")
            }
        }
    }

    private fun createPrivateKey(): SecKeyRef = memScoped {
        val keySizeValue = alloc<IntVar>().apply { value = 256 }
        val keySize = checkNotNull(
            CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, keySizeValue.ptr),
        )
        try {
            withDictionary { parameters ->
                withDictionary { privateKeyAttributes ->
                    val tag = DEVICE_KEY_TAG.encodeToByteArray().toCfData()
                    try {
                        CFDictionarySetValue(parameters, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
                        CFDictionarySetValue(parameters, kSecAttrKeySizeInBits, keySize)
                        CFDictionarySetValue(privateKeyAttributes, kSecAttrIsPermanent, kCFBooleanTrue)
                        CFDictionarySetValue(privateKeyAttributes, kSecAttrApplicationTag, tag)
                        CFDictionarySetValue(
                            privateKeyAttributes,
                            kSecAttrAccessible,
                            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        )
                        CFDictionarySetValue(parameters, kSecPrivateKeyAttrs, privateKeyAttributes)
                        checkNotNull(SecKeyCreateRandomKey(parameters, null)) {
                            "Unable to create the iOS companion proof key."
                        }
                    } finally {
                        CFRelease(tag)
                    }
                }
            }
        } finally {
            CFRelease(keySize)
        }
    }

    private inline fun <T> withKeyQuery(block: (platform.CoreFoundation.CFMutableDictionaryRef) -> T): T =
        withDictionary { query ->
            val tag = DEVICE_KEY_TAG.encodeToByteArray().toCfData()
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassKey)
                CFDictionarySetValue(query, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
                CFDictionarySetValue(query, kSecAttrApplicationTag, tag)
                block(query)
            } finally {
                CFRelease(tag)
            }
        }

    private inline fun <T> withDictionary(block: (platform.CoreFoundation.CFMutableDictionaryRef) -> T): T {
        val dictionary = checkNotNull(
            CFDictionaryCreateMutable(
                allocator = kCFAllocatorDefault,
                capacity = 0,
                keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
                valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
            ),
        )
        return try {
            block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }

    private companion object {
        const val DEVICE_KEY_TAG: String = "com.devuloopers.knet.companion.device-proof.v1"
        const val DEVICE_KEY_REFERENCE: String = "ios-keychain:$DEVICE_KEY_TAG"
        const val DEVICE_ID_BYTES: Int = 18
        val URL_SAFE_BASE64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}

private fun SecKeyRef.externalRepresentation(): ByteArray {
    val data = checkNotNull(SecKeyCopyExternalRepresentation(this, null)) {
        "iOS Security.framework could not export the companion public key."
    }
    return try {
        data.copyBytes()
    } finally {
        CFRelease(data)
    }
}

private fun ByteArray.asP256SubjectPublicKeyInfo(): ByteArray {
    require(size == P256_UNCOMPRESSED_POINT_BYTES && firstOrNull() == 0x04.toByte()) {
        "The iOS companion proof key is not an uncompressed P-256 public key."
    }
    return P256_SPKI_PREFIX + this
}

private fun ByteArray.toCfData(): platform.CoreFoundation.CFDataRef = usePinned { pinned ->
    checkNotNull(
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), size.toLong()),
    )
}

private fun platform.CoreFoundation.CFDataRef.copyBytes(): ByteArray {
    val length = CFDataGetLength(this)
    val source = checkNotNull(CFDataGetBytePtr(this))
    return ByteArray(length.toInt()) { index -> source[index].toByte() }
}

private const val P256_UNCOMPRESSED_POINT_BYTES: Int = 65
private val P256_SPKI_PREFIX: ByteArray = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
)
