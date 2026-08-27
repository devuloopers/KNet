package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionControlProtocol
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId

/** Closed set of authenticated companion control-plane operations. */
public enum class CompanionControlOperation {
    /** Complete a one-time pairing invitation and issue the initial device credential. */
    PAIR,

    /** Replace a valid paired-device credential atomically. */
    REFRESH_CREDENTIAL,

    /** Resolve canonical identity and current ports after DNS-SD supplied only an untrusted address. */
    RECONCILE_ENDPOINTS,
}

/** Device credential transmitted only after the pinned desktop TLS identity is authenticated. */
public class CompanionControlAuthorization(
    /** Stable paired-device identity presented with the credential. */
    public val deviceId: RegisteredDeviceId,
    credential: String,
) {
    private val secret: String = credential

    init {
        require(deviceId.value.matches(SAFE_AUTHORIZATION_TOKEN)) {
            "Companion control device identity is not safe for authorization transport."
        }
        require(secret.length in 16..512 && secret.matches(SAFE_AUTHORIZATION_TOKEN)) {
            "Companion control credential is not safe for authorization transport."
        }
    }

    /** Returns the credential to the platform transport without retaining another mutable representation. */
    public fun credential(): String = secret

    private companion object {
        val SAFE_AUTHORIZATION_TOKEN: Regex = Regex("[A-Za-z0-9._~-]{1,512}")
    }
}

/**
 * Secret-bearing request passed to a platform-owned pinned-TLS transport.
 *
 * The transport must verify [rootCertificateSha256], PKIX trust rooted at [rootCertificate], HTTPS hostname, and
 * [transportIdentitySha256] before writing the body or [authorization] to the network.
 */
public class CompanionControlRequest(
    /** Reachable secure desktop control endpoint. */
    public val endpoint: CompanionServiceEndpoint,
    /** Exact expected certificate-chain transport identity. */
    public val transportIdentitySha256: Sha256Fingerprint,
    /** Exact expected root certificate fingerprint. */
    public val rootCertificateSha256: Sha256Fingerprint,
    /** Public root promoted into platform PKIX trust only after fingerprint validation. */
    public val rootCertificate: CompanionRootCertificate,
    /** Typed control operation that determines the wire path and media types. */
    public val operation: CompanionControlOperation,
    body: ByteArray,
    /** Optional paired-device authorization; pairing itself is authorized by its invitation and proof. */
    public val authorization: CompanionControlAuthorization? = null,
) {
    private val content: ByteArray = body.copyOf()

    init {
        require(endpoint.secure) { "Companion control requests require a secure endpoint." }
        require(content.size in 1..CompanionControlProtocol.MAXIMUM_REQUEST_BYTES) {
            "Companion control request body size is invalid."
        }
        require(operation != CompanionControlOperation.PAIR || authorization == null) {
            "Pairing requests must not carry an existing device credential."
        }
        require(operation != CompanionControlOperation.REFRESH_CREDENTIAL || authorization != null) {
            "Credential refresh requests require device authorization."
        }
        require(operation != CompanionControlOperation.RECONCILE_ENDPOINTS || authorization != null) {
            "Endpoint reconciliation requests require device authorization."
        }
    }

    /** Returns a defensive copy of the bounded request body. */
    public fun copyBody(): ByteArray = content.copyOf()
}

/** Bounded response returned by a platform-owned companion control transport. */
public class CompanionControlResponse(
    /** HTTP-compatible status returned by the authenticated desktop. */
    public val statusCode: Int,
    body: ByteArray,
) {
    private val content: ByteArray = body.copyOf()

    init {
        require(statusCode in 100..599) { "Companion control status is invalid." }
        require(content.size <= CompanionControlProtocol.MAXIMUM_RESPONSE_BYTES) {
            "Companion control response body is too large."
        }
    }

    /** Returns a defensive copy of the bounded response body. */
    public fun copyBody(): ByteArray = content.copyOf()
}

/** Platform transport boundary for pinned-root, identity-checked companion control requests. */
public fun interface CompanionControlTransport {
    /** Executes [request] only after all required TLS trust and identity checks succeed. */
    public suspend fun execute(request: CompanionControlRequest): CompanionControlResponse
}
