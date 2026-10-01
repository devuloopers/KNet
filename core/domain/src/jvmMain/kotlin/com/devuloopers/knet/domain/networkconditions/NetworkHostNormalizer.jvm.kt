package com.devuloopers.knet.domain.networkconditions

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

actual object NetworkHostNormalizer {
    actual fun normalize(value: String): String? {
        var candidate = value.trim()
        if (candidate.startsWith('[') && candidate.endsWith(']')) candidate = candidate.drop(1).dropLast(1)
        candidate = candidate.trimEnd('.').lowercase()
        if (candidate.isBlank() || candidate.any(Char::isWhitespace)) return null
        if (candidate.contains(':')) {
            return runCatching { InetAddress.getByName(candidate) as? Inet6Address }
                .getOrNull()
                ?.hostAddress
                ?.substringBefore('%')
                ?.lowercase()
        }
        if (candidate.all { it.isDigit() || it == '.' }) {
            val octets = candidate.split('.')
            if (octets.size != 4) return null
            return octets.map { it.toIntOrNull()?.takeIf { number -> number in 0..255 } ?: return null }
                .joinToString(".")
        }
        return runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrNull()
            ?.takeIf { it.length <= 253 && it.split('.').all { label -> label.isNotBlank() && label.length <= 63 } }
    }

    actual fun isIpLiteral(normalizedValue: String): Boolean =
        normalizedValue.contains(':') || normalizedValue.split('.').let { parts ->
            parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        }
}
