package com.devuloopers.knet.companion.data.ios

import com.devuloopers.knet.companion.data.store.CompanionSecretProtector
import com.devuloopers.knet.companion.data.store.ProtectedCompanionSecret
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Keychain-backed iOS protector; DataStore persists only an opaque reference marker. */
@OptIn(ExperimentalForeignApi::class)
internal class IosKeychainCompanionSecretProtector : CompanionSecretProtector {
    override suspend fun protect(key: String, value: String): ProtectedCompanionSecret {
        require(key.isNotBlank() && value.isNotBlank())
        writeKeychainValue(key, value)
        return ProtectedCompanionSecret(KEYCHAIN_REFERENCE_MARKER)
    }

    override suspend fun reveal(key: String, secret: ProtectedCompanionSecret): String? {
        require(key.isNotBlank())
        if (secret.serializedValue != KEYCHAIN_REFERENCE_MARKER) return null
        return readKeychainValue(key)
    }

    override suspend fun remove(key: String) {
        require(key.isNotBlank())
        withBaseQuery(key) { query ->
            val status = SecItemDelete(query)
            check(status == errSecSuccess || status == errSecItemNotFound) {
                "Unable to delete companion credential from iOS Keychain (status=$status)."
            }
        }
    }

    private fun writeKeychainValue(key: String, value: String) {
        val bytes = value.encodeToByteArray()
        bytes.usePinned { pinned ->
            val data = checkNotNull(
                CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.toLong()),
            )
            try {
                withBaseQuery(key) { query ->
                    CFDictionarySetValue(query, kSecValueData, data)
                    CFDictionarySetValue(
                        query,
                        kSecAttrAccessible,
                        kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    )
                    when (val status = SecItemAdd(query, null)) {
                        errSecSuccess -> Unit
                        errSecDuplicateItem -> updateKeychainValue(key, data)
                        else -> error("Unable to persist companion credential in iOS Keychain (status=$status).")
                    }
                }
            } finally {
                CFRelease(data)
            }
        }
    }

    private fun updateKeychainValue(key: String, data: kotlinx.cinterop.CValuesRef<*>) {
        withBaseQuery(key) { query ->
            withDictionary { attributes ->
                CFDictionarySetValue(attributes, kSecValueData, data)
                val status = SecItemUpdate(query, attributes)
                check(status == errSecSuccess) {
                    "Unable to update companion credential in iOS Keychain (status=$status)."
                }
            }
        }
    }

    private fun readKeychainValue(key: String): String? = withBaseQuery(key) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<COpaquePointerVar>()
            result.value = null
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecItemNotFound -> null
                errSecSuccess -> {
                    val data = checkNotNull(result.value)
                    val dataReference: CFDataRef = data.reinterpret()
                    try {
                        val length = CFDataGetLength(dataReference)
                        val source = checkNotNull(CFDataGetBytePtr(dataReference))
                        ByteArray(length.toInt()) { index -> source[index].toByte() }
                            .decodeToString(throwOnInvalidSequence = true)
                    } finally {
                        CFRelease(data)
                    }
                }
                else -> error("Unable to read companion credential from iOS Keychain (status=$status).")
            }
        }
    }

    private inline fun <T> withBaseQuery(key: String, block: (platform.CoreFoundation.CFMutableDictionaryRef) -> T): T =
        withDictionary { query ->
            val service = checkNotNull(
                CFStringCreateWithCString(kCFAllocatorDefault, KEYCHAIN_SERVICE, kCFStringEncodingUTF8),
            )
            val account = checkNotNull(
                CFStringCreateWithCString(kCFAllocatorDefault, key, kCFStringEncodingUTF8),
            )
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(query, kSecAttrService, service)
                CFDictionarySetValue(query, kSecAttrAccount, account)
                block(query)
            } finally {
                CFRelease(account)
                CFRelease(service)
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
        const val KEYCHAIN_SERVICE: String = "com.devuloopers.knet.companion.credentials"
        const val KEYCHAIN_REFERENCE_MARKER: String = "keychain:v1"
    }
}
