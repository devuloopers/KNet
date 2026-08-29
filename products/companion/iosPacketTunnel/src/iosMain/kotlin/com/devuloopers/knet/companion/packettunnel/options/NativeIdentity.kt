@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.packettunnel.options

import com.devuloopers.knet.companion.packettunnel.hev.knet_ip_address_family
import com.devuloopers.knet.companion.packettunnel.hev.knet_sha256
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned

internal enum class IpAddressFamily {
    IPV4,
    IPV6,
}

internal fun ipAddressFamily(value: String): IpAddressFamily? = when (knet_ip_address_family(value)) {
    4 -> IpAddressFamily.IPV4
    6 -> IpAddressFamily.IPV6
    else -> null
}

internal fun ByteArray.sha256Hex(): String = memScoped {
    val input = this@sha256Hex
    val digest = allocArray<UByteVar>(SHA256_BYTES)
    if (input.isEmpty()) {
        knet_sha256(null, 0u, digest)
    } else {
        input.usePinned { pinned ->
            knet_sha256(pinned.addressOf(0).reinterpret(), input.size.toUInt(), digest)
        }
    }
    buildString(SHA256_HEX_LENGTH) {
        repeat(SHA256_BYTES) { index ->
            val value = digest[index].toInt()
            append(HEX[value ushr 4])
            append(HEX[value and 0x0f])
        }
    }
}

private const val SHA256_BYTES: Int = 32
private const val SHA256_HEX_LENGTH: Int = 64
private const val HEX: String = "0123456789abcdef"
