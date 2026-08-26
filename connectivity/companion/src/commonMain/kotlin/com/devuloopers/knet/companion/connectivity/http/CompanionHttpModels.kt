package com.devuloopers.knet.companion.connectivity.http

import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint

/** HTTP methods used by the bounded companion control plane. */
internal enum class CompanionHttpMethod {
    GET,
    POST,
}

/** Closed TLS policies supported by the companion HTTP transport. */
internal sealed interface CompanionHttpSecurity {
    /** Cleartext LAN capability restricted to the public root identified by the scanned QR fingerprint. */
    data object BootstrapRootOnly : CompanionHttpSecurity

    /** Private-root TLS used while onboarding or communicating with an already paired desktop. */
    class PinnedRoot(
        val rootCertificate: CompanionRootCertificate,
        val rootCertificateSha256: Sha256Fingerprint,
        val transportIdentitySha256: Sha256Fingerprint,
    ) : CompanionHttpSecurity

    /** Native system trust used to prove that the user installed and enabled the paired KNet root. */
    class PlatformTrusted(
        val expectedRootCertificate: CompanionRootCertificate,
        val expectedRootCertificateSha256: Sha256Fingerprint,
        val transportIdentitySha256: Sha256Fingerprint,
    ) : CompanionHttpSecurity
}

/** Immutable bounded request executed by a platform-configured Ktor client. */
internal class CompanionHttpRequest(
    val endpoint: CompanionServiceEndpoint,
    val method: CompanionHttpMethod,
    val path: String,
    val requestMediaType: String? = null,
    val acceptedMediaType: String? = null,
    val authorization: String? = null,
    val additionalHeaders: Map<String, String> = emptyMap(),
    body: ByteArray = ByteArray(0),
    val maximumResponseBytes: Int,
    val security: CompanionHttpSecurity,
) {
    private val content: ByteArray = body.copyOf()

    init {
        require(path.startsWith('/') && ".." !in path && path.none(Char::isHttpControl)) {
            "Companion HTTP path is invalid."
        }
        require(maximumResponseBytes >= 0) { "Companion HTTP response limit must not be negative." }
        require(additionalHeaders.keys.all(HTTP_HEADER_NAME::matches)) { "Companion HTTP header names are invalid." }
        require(additionalHeaders.keys.none { name -> name.lowercase() in RESERVED_HEADERS }) {
            "Companion HTTP protected headers cannot be overridden."
        }
        require(additionalHeaders.values.none { value -> value.any(Char::isHttpControl) }) {
            "Companion HTTP header values contain control characters."
        }
        require(authorization?.none(Char::isHttpControl) != false) {
            "Companion HTTP authorization contains control characters."
        }
        require(requestMediaType?.none(Char::isHttpControl) != false) {
            "Companion HTTP request media type contains control characters."
        }
        require(acceptedMediaType?.none(Char::isHttpControl) != false) {
            "Companion HTTP accepted media type contains control characters."
        }
        when (security) {
            CompanionHttpSecurity.BootstrapRootOnly -> {
                require(!endpoint.secure) { "Bootstrap root retrieval requires a non-secure endpoint." }
                require(method == CompanionHttpMethod.GET) { "Bootstrap root retrieval must use GET." }
                require(path == CompanionBootstrapProtocol.ROOT_CERTIFICATE_PATH) {
                    "Bootstrap root retrieval path is invalid."
                }
                require(
                    requestMediaType == null &&
                        acceptedMediaType == CompanionBootstrapProtocol.ROOT_CERTIFICATE_MEDIA_TYPE,
                ) {
                    "Bootstrap root retrieval media policy is invalid."
                }
                require(authorization == null && additionalHeaders.isEmpty() && content.isEmpty()) {
                    "Bootstrap root retrieval cannot carry credentials, custom headers, or a body."
                }
                require(maximumResponseBytes in 1..CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES) {
                    "Bootstrap root retrieval response limit is invalid."
                }
            }
            is CompanionHttpSecurity.PinnedRoot,
            is CompanionHttpSecurity.PlatformTrusted,
                -> require(endpoint.secure) { "Authenticated companion HTTP requires a secure endpoint." }
        }
    }

    fun copyBody(): ByteArray = content.copyOf()

    val tlsServerName: String?
        get() = when (security) {
            CompanionHttpSecurity.BootstrapRootOnly -> null
            is CompanionHttpSecurity.PinnedRoot,
            is CompanionHttpSecurity.PlatformTrusted,
                -> CompanionCertificateProtocol.TLS_SERVER_NAME
        }
}

private fun Char.isHttpControl(): Boolean = code < 0x20 || code == 0x7f

private val HTTP_HEADER_NAME: Regex = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
private val RESERVED_HEADERS: Set<String> = setOf(
    "accept",
    "authorization",
    "content-length",
    "content-type",
    "host",
)

/** Immutable bounded response returned by the shared Ktor exchange. */
internal class CompanionHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    body: ByteArray,
) {
    private val content: ByteArray = body.copyOf()

    fun copyBody(): ByteArray = content.copyOf()

    val mediaType: String?
        get() = headers["content-type"]?.substringBefore(';')?.trim()?.lowercase()
}

/** Security failures raised before a secret-bearing HTTP request can be transmitted. */
internal sealed class CompanionHttpSecurityException(message: String) : Exception(message) {
    class TrustRejected : CompanionHttpSecurityException("Companion TLS trust validation failed.")
    class IdentityRejected : CompanionHttpSecurityException("Companion TLS identity validation failed.")
}
