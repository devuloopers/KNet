package com.devuloopers.knet.companion.packettunnel.options

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal data class TunnelStartOptions(
    val desktopId: Uuid,
    val proxyHost: String,
    val proxyAddressFamily: IpAddressFamily,
    val proxyPort: UShort,
    val authorization: String,
    val rootCertificate: ByteArray,
    val rootSha256: String,
    val transportSha256: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TunnelStartOptions

        if (desktopId != other.desktopId) return false
        if (proxyHost != other.proxyHost) return false
        if (proxyAddressFamily != other.proxyAddressFamily) return false
        if (proxyPort != other.proxyPort) return false
        if (authorization != other.authorization) return false
        if (!rootCertificate.contentEquals(other.rootCertificate)) return false
        if (rootSha256 != other.rootSha256) return false
        if (transportSha256 != other.transportSha256) return false

        return true
    }

    override fun hashCode(): Int {
        var result = desktopId.hashCode()
        result = 31 * result + proxyHost.hashCode()
        result = 31 * result + proxyAddressFamily.hashCode()
        result = 31 * result + proxyPort.hashCode()
        result = 31 * result + authorization.hashCode()
        result = 31 * result + rootCertificate.contentHashCode()
        result = 31 * result + rootSha256.hashCode()
        result = 31 * result + transportSha256.hashCode()
        return result
    }
}

internal object TunnelStartOptionsParser {
    fun parse(values: Map<Any?, *>?): TunnelStartOptions {
        val options = values ?: invalid()
        val schemaVersion = options.string(TunnelOptionKey.SCHEMA_VERSION)
        val desktopIdValue = options.string(TunnelOptionKey.DESKTOP_ID)
        val desktopId = runCatching { Uuid.parse(desktopIdValue) }.getOrNull() ?: invalid()
        val host = options.string(TunnelOptionKey.PROXY_HOST)
        val family = ipAddressFamily(host) ?: invalid()
        val portValue = options.number(TunnelOptionKey.PROXY_PORT)?.toInt() ?: invalid()
        val authorization = options.string(TunnelOptionKey.AUTHORIZATION)
        val rootCertificate = runCatching {
            Base64.decode(options.string(TunnelOptionKey.ROOT_CERTIFICATE))
        }.getOrNull() ?: invalid()
        val rootSha256 = options.string(TunnelOptionKey.ROOT_SHA256)
        val transportSha256 = options.string(TunnelOptionKey.TRANSPORT_SHA256)

        if (
            schemaVersion != SCHEMA_VERSION ||
            desktopId.toString() != desktopIdValue ||
            portValue !in 1..UShort.MAX_VALUE.toInt() ||
            !authorization.isSafeAuthorization() ||
            rootCertificate.isEmpty() ||
            !rootSha256.isFingerprint() ||
            !transportSha256.isFingerprint() ||
            options.string(TunnelOptionKey.UNSUPPORTED_POLICY) != UNSUPPORTED_POLICY ||
            rootCertificate.sha256Hex() != rootSha256
        ) {
            invalid()
        }

        return TunnelStartOptions(
            desktopId = desktopId,
            proxyHost = host,
            proxyAddressFamily = family,
            proxyPort = portValue.toUShort(),
            authorization = authorization,
            rootCertificate = rootCertificate,
            rootSha256 = rootSha256,
            transportSha256 = transportSha256,
        )
    }

    private fun Map<Any?, *>.string(key: TunnelOptionKey): String = this[key.wireName] as? String ?: invalid()

    private fun Map<Any?, *>.number(key: TunnelOptionKey): Number? = this[key.wireName] as? Number

    private fun String.isSafeAuthorization(): Boolean =
        length <= MAXIMUM_AUTHORIZATION_LENGTH && startsWith(BEARER_PREFIX) && all { it.code >= 0x20 && it.code != 0x7f }

    private fun String.isFingerprint(): Boolean = length == SHA256_HEX_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }

    private fun invalid(): Nothing = throw TunnelFailure.INVALID_START_OPTIONS.exception()

    private const val SCHEMA_VERSION: String = "1"
    private const val UNSUPPORTED_POLICY: String = "REJECT"
    private const val BEARER_PREFIX: String = "Bearer "
    private const val MAXIMUM_AUTHORIZATION_LENGTH: Int = 600
    private const val SHA256_HEX_LENGTH: Int = 64
}

private enum class TunnelOptionKey(val wireName: String) {
    SCHEMA_VERSION("schemaVersion"),
    DESKTOP_ID("desktopId"),
    PROXY_HOST("proxyHost"),
    PROXY_PORT("proxyPort"),
    AUTHORIZATION("authorization"),
    ROOT_CERTIFICATE("rootCertificate"),
    ROOT_SHA256("rootSha256"),
    TRANSPORT_SHA256("transportSha256"),
    UNSUPPORTED_POLICY("unsupportedPolicy"),
}
