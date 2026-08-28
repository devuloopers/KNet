package com.devuloopers.knet.companion.model

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.io.encoding.Base64

/** Canonical codec for the small secret-bearing `knet://pair/v3` bootstrap QR payload. */
public class CompanionBootstrapPayloadCodec {
    /** Encodes [bootstrap] into the canonical version-3 pairing URI. */
    public fun encode(bootstrap: CompanionPairingBootstrap): String = buildString {
        append(PREFIX)
        append(
            PairingFormCodec.encode(
                listOf(
                    "id" to bootstrap.id.value,
                    "secret" to bootstrap.retrievalSecret.value,
                    "expires" to bootstrap.expiresAtEpochMillis.toString(),
                    "rootHost" to bootstrap.rootCertificateEndpoint.host,
                    "rootPort" to bootstrap.rootCertificateEndpoint.port.toString(),
                    "redeemHost" to bootstrap.retrievalEndpoint.host,
                    "redeemPort" to bootstrap.retrievalEndpoint.port.toString(),
                    "transportPin" to bootstrap.transportIdentitySha256.value,
                    "rootPin" to bootstrap.rootCertificateSha256.value,
                ),
            ),
        )
    }

    /** Decodes and validates one canonical version-3 [payload]. */
    public fun decode(payload: String): CompanionPairingBootstrap {
        require(payload.length <= MAXIMUM_BOOTSTRAP_CHARACTERS) { "Pairing bootstrap is too large." }
        val query = payload.removePrefix(PREFIX).takeIf { payload.startsWith(PREFIX) }
            ?: throw IllegalArgumentException("Unsupported pairing bootstrap scheme or version.")
        val fields = PairingFormCodec.decode(query)
        require(fields.keys == EXPECTED_FIELDS) { "Pairing bootstrap fields do not match protocol version 3." }
        return CompanionPairingBootstrap(
            protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
            id = CompanionBootstrapId(PairingFormCodec.required(fields, "id")),
            retrievalSecret = CompanionBootstrapSecret(PairingFormCodec.required(fields, "secret")),
            expiresAtEpochMillis = PairingFormCodec.required(fields, "expires").toLong(),
            rootCertificateEndpoint = CompanionServiceEndpoint(
                host = PairingFormCodec.required(fields, "rootHost"),
                port = PairingFormCodec.required(fields, "rootPort").toInt(),
                scheme = CompanionEndpointScheme.HTTP,
            ),
            retrievalEndpoint = CompanionServiceEndpoint(
                host = PairingFormCodec.required(fields, "redeemHost"),
                port = PairingFormCodec.required(fields, "redeemPort").toInt(),
                scheme = CompanionEndpointScheme.HTTPS,
            ),
            transportIdentitySha256 = Sha256Fingerprint(PairingFormCodec.required(fields, "transportPin")),
            rootCertificateSha256 = Sha256Fingerprint(PairingFormCodec.required(fields, "rootPin")),
        )
    }

    private companion object {
        const val PREFIX: String = "knet://pair/v3?"
        const val MAXIMUM_BOOTSTRAP_CHARACTERS: Int = 2 * 1024
        val EXPECTED_FIELDS: Set<String> = setOf(
            "id",
            "secret",
            "expires",
            "rootHost",
            "rootPort",
            "redeemHost",
            "redeemPort",
            "transportPin",
            "rootPin",
        )
    }
}

/**
 * Secret-bearing request used to consume one bootstrap record without placing its secret in the URL.
 *
 * @property id opaque one-time bootstrap record identity.
 * @property retrievalSecret one-time secret transmitted only after pinned TLS succeeds.
 */
public data class CompanionBootstrapRedemptionRequest(
    public val id: CompanionBootstrapId,
    public val retrievalSecret: CompanionBootstrapSecret,
)

/** Canonical bounded body codec for [CompanionBootstrapProtocol.REDEEM_PATH]. */
public class CompanionBootstrapRedemptionCodec {
    /** Encodes [request] into the redemption request body. */
    public fun encode(request: CompanionBootstrapRedemptionRequest): ByteArray = PairingFormCodec.encode(
        listOf("id" to request.id.value, "secret" to request.retrievalSecret.value),
    ).encodeToByteArray()

    /** Decodes a bounded redemption request [body]. */
    public fun decode(body: ByteArray): CompanionBootstrapRedemptionRequest {
        require(body.size <= CompanionBootstrapProtocol.MAXIMUM_REQUEST_BYTES) {
            "Bootstrap redemption request is too large."
        }
        val fields = PairingFormCodec.decode(body.decodeToString(throwOnInvalidSequence = true))
        require(fields.keys == EXPECTED_FIELDS) { "Bootstrap redemption fields are invalid." }
        return CompanionBootstrapRedemptionRequest(
            id = CompanionBootstrapId(PairingFormCodec.required(fields, "id")),
            retrievalSecret = CompanionBootstrapSecret(PairingFormCodec.required(fields, "secret")),
        )
    }

    private companion object {
        val EXPECTED_FIELDS: Set<String> = setOf("id", "secret")
    }
}

/** Canonical response codec for the complete invitation returned after bootstrap redemption. */
public class CompanionInvitationResponseCodec {
    /** Encodes [invitation] into a bounded transport response body. */
    public fun encode(invitation: CompanionPairingInvitation): ByteArray = PairingFormCodec.encode(
        listOf(
            "version" to invitation.protocolVersion.toString(),
            "desktopId" to invitation.desktopId.value,
            "desktopName" to invitation.desktopDisplayName.value,
            "pairingId" to invitation.pairing.id.value,
            "pairingSecret" to invitation.pairing.secret,
            "expires" to invitation.pairing.expiresAtEpochMillis.toString(),
            "scopes" to invitation.pairing.scopes.sortedBy(DeviceScope::name)
                .joinToString(",", transform = DeviceScope::name),
            "controlHost" to invitation.controlEndpoint.host,
            "controlPort" to invitation.controlEndpoint.port.toString(),
            "proxyHost" to invitation.proxyEndpoint.host,
            "proxyPort" to invitation.proxyEndpoint.port.toString(),
            "transportPin" to invitation.transportIdentitySha256.value,
            "rootPin" to invitation.rootCertificateSha256.value,
            "rootDer" to ROOT_CERTIFICATE_ENCODING.encode(invitation.rootCertificate.copyBytes()),
        ),
    ).encodeToByteArray().also { body ->
        require(body.size <= CompanionBootstrapProtocol.MAXIMUM_RESPONSE_BYTES) {
            "Pairing invitation response is too large."
        }
    }

    /** Decodes a complete bounded invitation response [body]. */
    public fun decode(body: ByteArray): CompanionPairingInvitation {
        require(body.size <= CompanionBootstrapProtocol.MAXIMUM_RESPONSE_BYTES) {
            "Pairing invitation response is too large."
        }
        val fields = PairingFormCodec.decode(body.decodeToString(throwOnInvalidSequence = true))
        require(fields.keys == EXPECTED_FIELDS) { "Pairing invitation response fields are invalid." }
        val protocolVersion = PairingFormCodec.required(fields, "version").toInt()
        val scopes = PairingFormCodec.required(fields, "scopes")
            .split(',')
            .filter(String::isNotBlank)
            .map(DeviceScope::valueOf)
            .toSet()
        return CompanionPairingInvitation(
            protocolVersion = protocolVersion,
            desktopId = CompanionDesktopId(PairingFormCodec.required(fields, "desktopId")),
            desktopDisplayName = CompanionDesktopDisplayName(PairingFormCodec.required(fields, "desktopName")),
            pairing = PairingInvitation(
                id = PairingInvitationId(PairingFormCodec.required(fields, "pairingId")),
                secret = PairingFormCodec.required(fields, "pairingSecret"),
                expiresAtEpochMillis = PairingFormCodec.required(fields, "expires").toLong(),
                scopes = scopes,
            ),
            controlEndpoint = CompanionServiceEndpoint(
                PairingFormCodec.required(fields, "controlHost"),
                PairingFormCodec.required(fields, "controlPort").toInt(),
                scheme = CompanionEndpointScheme.HTTPS,
            ),
            proxyEndpoint = CompanionServiceEndpoint(
                PairingFormCodec.required(fields, "proxyHost"),
                PairingFormCodec.required(fields, "proxyPort").toInt(),
                scheme = CompanionEndpointScheme.HTTPS,
            ),
            transportIdentitySha256 = Sha256Fingerprint(PairingFormCodec.required(fields, "transportPin")),
            rootCertificateSha256 = Sha256Fingerprint(PairingFormCodec.required(fields, "rootPin")),
            rootCertificate = CompanionRootCertificate(
                ROOT_CERTIFICATE_ENCODING.decode(PairingFormCodec.required(fields, "rootDer")),
            ),
        )
    }

    private companion object {
        val ROOT_CERTIFICATE_ENCODING: Base64 =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        val EXPECTED_FIELDS: Set<String> = setOf(
            "version",
            "desktopId",
            "desktopName",
            "pairingId",
            "pairingSecret",
            "expires",
            "scopes",
            "controlHost",
            "controlPort",
            "proxyHost",
            "proxyPort",
            "transportPin",
            "rootPin",
            "rootDer",
        )
    }
}

/** Shared paths, media types, and bounds for the authenticated companion control plane. */
public object CompanionControlProtocol {
    /** Completes one pairing invitation and issues the first device credential. */
    public const val PAIR_PATH: String = "/companion/v1/pair"

    /** Atomically rotates the credential of one already paired device. */
    public const val REFRESH_PATH: String = "/companion/v1/credentials/refresh"

    /** Authenticates a discovered address and returns canonical identity plus current service ports. */
    public const val RECONCILE_PATH: String = "/companion/v1/endpoints/reconcile"

    /** Media type for a proof-bearing pairing completion request. */
    public const val PAIR_REQUEST_MEDIA_TYPE: String = "application/vnd.knet.companion-pair-request"

    /** Media type for the initial credential grant. */
    public const val PAIR_RESPONSE_MEDIA_TYPE: String = "application/vnd.knet.companion-pair-grant"

    /** Media type for a credential rotation request. */
    public const val REFRESH_REQUEST_MEDIA_TYPE: String = "application/vnd.knet.companion-refresh-request"

    /** Media type for a rotated credential grant. */
    public const val REFRESH_RESPONSE_MEDIA_TYPE: String = "application/vnd.knet.companion-refresh-grant"

    public const val RECONCILE_REQUEST_MEDIA_TYPE: String = "application/vnd.knet.companion-endpoint-request"
    public const val RECONCILE_RESPONSE_MEDIA_TYPE: String = "application/vnd.knet.companion-endpoint-descriptor"

    /** Maximum accepted request body across control-plane operations. */
    public const val MAXIMUM_REQUEST_BYTES: Int = 32 * 1024

    /** Maximum accepted response body across control-plane operations. */
    public const val MAXIMUM_RESPONSE_BYTES: Int = 32 * 1024
}

/** Canonical bounded codec for proof-bearing pairing completion requests. */
public class CompanionPairingCompletionCodec {
    /** Encodes [request] into the canonical pairing completion request body. */
    public fun encode(request: PairingCompletionRequest): ByteArray = PairingFormCodec.encode(
        listOf(
            "invitationId" to request.invitationId.value,
            "invitationSecret" to request.invitationSecret,
            "deviceId" to request.deviceId.value,
            "displayName" to request.displayName,
            "publicKey" to request.publicKeyEncoded,
            "proofSignature" to request.proofSignatureEncoded,
            "proofAlgorithm" to request.proofAlgorithm.name,
        ),
    ).encodeToByteArray().requireControlRequestSize()

    /** Decodes one canonical, bounded pairing completion [body]. */
    public fun decode(body: ByteArray): PairingCompletionRequest {
        body.requireControlRequestSize()
        val fields = PairingFormCodec.decode(body.decodeToString(throwOnInvalidSequence = true))
        require(fields.keys == EXPECTED_FIELDS) { "Pairing completion fields are invalid." }
        return PairingCompletionRequest(
            invitationId = PairingInvitationId(PairingFormCodec.required(fields, "invitationId")),
            invitationSecret = PairingFormCodec.required(fields, "invitationSecret"),
            deviceId = RegisteredDeviceId(PairingFormCodec.required(fields, "deviceId")),
            displayName = PairingFormCodec.required(fields, "displayName"),
            publicKeyEncoded = PairingFormCodec.required(fields, "publicKey"),
            proofSignatureEncoded = PairingFormCodec.required(fields, "proofSignature"),
            proofAlgorithm = DeviceProofAlgorithm.valueOf(PairingFormCodec.required(fields, "proofAlgorithm")),
        )
    }

    private companion object {
        val EXPECTED_FIELDS: Set<String> = setOf(
            "invitationId",
            "invitationSecret",
            "deviceId",
            "displayName",
            "publicKey",
            "proofSignature",
            "proofAlgorithm",
        )
    }
}

/** Secret-bearing initial credential grant returned exactly once after pairing succeeds. */
public data class CompanionPairingGrant(
    /** Plain credential consumed immediately by platform-protected storage. */
    public val credential: String,
    /** Explicit capabilities granted to the paired device. */
    public val scopes: Set<DeviceScope>,
    /** Absolute expiry of [credential]. */
    public val credentialExpiresAtEpochMillis: Long,
) {
    init {
        require(credential.length in 16..512) { "Pairing credential length is invalid." }
        require(scopes.isNotEmpty()) { "A pairing grant must contain at least one scope." }
        require(credentialExpiresAtEpochMillis > 0L) { "Pairing credential expiry must be positive." }
    }
}

/** Canonical bounded codec for the initial credential grant. */
public class CompanionPairingGrantCodec {
    /** Encodes [grant] into the canonical pairing grant response body. */
    public fun encode(grant: CompanionPairingGrant): ByteArray = PairingFormCodec.encode(
        listOf(
            "credential" to grant.credential,
            "scopes" to grant.scopes.sortedBy(DeviceScope::name).joinToString(",", transform = DeviceScope::name),
            "expires" to grant.credentialExpiresAtEpochMillis.toString(),
        ),
    ).encodeToByteArray().requireControlResponseSize()

    /** Decodes one canonical, bounded pairing grant [body]. */
    public fun decode(body: ByteArray): CompanionPairingGrant {
        body.requireControlResponseSize()
        val fields = PairingFormCodec.decode(body.decodeToString(throwOnInvalidSequence = true))
        require(fields.keys == EXPECTED_FIELDS) { "Pairing grant fields are invalid." }
        return CompanionPairingGrant(
            credential = PairingFormCodec.required(fields, "credential"),
            scopes = PairingFormCodec.required(fields, "scopes")
                .split(',')
                .filter(String::isNotBlank)
                .map(DeviceScope::valueOf)
                .toSet(),
            credentialExpiresAtEpochMillis = PairingFormCodec.required(fields, "expires").toLong(),
        )
    }

    private companion object {
        val EXPECTED_FIELDS: Set<String> = setOf("credential", "scopes", "expires")
    }
}

/** Credential rotation request bound to the authenticated device identity. */
public data class CompanionCredentialRefreshRequest(
    /** Paired device whose current credential must be replaced. */
    public val deviceId: RegisteredDeviceId,
)

/** Canonical bounded codec for credential rotation requests. */
public class CompanionCredentialRefreshRequestCodec {
    /** Encodes [request] into the canonical refresh request body. */
    public fun encode(request: CompanionCredentialRefreshRequest): ByteArray = PairingFormCodec.encode(
        listOf("deviceId" to request.deviceId.value),
    ).encodeToByteArray().requireControlRequestSize()

    /** Decodes one canonical, bounded credential refresh [body]. */
    public fun decode(body: ByteArray): CompanionCredentialRefreshRequest {
        body.requireControlRequestSize()
        val fields = PairingFormCodec.decode(body.decodeToString(throwOnInvalidSequence = true))
        require(fields.keys == EXPECTED_FIELDS) { "Credential refresh fields are invalid." }
        return CompanionCredentialRefreshRequest(
            RegisteredDeviceId(PairingFormCodec.required(fields, "deviceId")),
        )
    }

    private companion object {
        val EXPECTED_FIELDS: Set<String> = setOf("deviceId")
    }
}

/** Secret-bearing credential rotation result consumed immediately by protected storage. */
public data class CompanionCredentialRefreshGrant(
    /** Newly issued credential that invalidates the previous value. */
    public val credential: String,
    /** Absolute expiry of [credential]. */
    public val credentialExpiresAtEpochMillis: Long,
) {
    init {
        require(credential.length in 16..512) { "Refreshed credential length is invalid." }
        require(credentialExpiresAtEpochMillis > 0L) { "Refreshed credential expiry must be positive." }
    }
}

/** Canonical bounded codec for credential rotation grants. */
public class CompanionCredentialRefreshGrantCodec {
    /** Encodes [grant] into the canonical refresh grant response body. */
    public fun encode(grant: CompanionCredentialRefreshGrant): ByteArray = PairingFormCodec.encode(
        listOf(
            "credential" to grant.credential,
            "expires" to grant.credentialExpiresAtEpochMillis.toString(),
        ),
    ).encodeToByteArray().requireControlResponseSize()

    /** Decodes one canonical, bounded credential refresh grant [body]. */
    public fun decode(body: ByteArray): CompanionCredentialRefreshGrant {
        body.requireControlResponseSize()
        val fields = PairingFormCodec.decode(body.decodeToString(throwOnInvalidSequence = true))
        require(fields.keys == EXPECTED_FIELDS) { "Credential refresh grant fields are invalid." }
        return CompanionCredentialRefreshGrant(
            credential = PairingFormCodec.required(fields, "credential"),
            credentialExpiresAtEpochMillis = PairingFormCodec.required(fields, "expires").toLong(),
        )
    }

    private companion object {
        val EXPECTED_FIELDS: Set<String> = setOf("credential", "expires")
    }
}

private fun ByteArray.requireControlRequestSize(): ByteArray = also {
    require(size <= CompanionControlProtocol.MAXIMUM_REQUEST_BYTES) { "Companion control request is too large." }
}

private fun ByteArray.requireControlResponseSize(): ByteArray = also {
    require(size <= CompanionControlProtocol.MAXIMUM_RESPONSE_BYTES) { "Companion control response is too large." }
}

private object PairingFormCodec {
    private const val HEX: String = "0123456789ABCDEF"

    fun encode(fields: List<Pair<String, String>>): String = fields.joinToString("&") { (key, value) ->
        "$key=${percentEncode(value)}"
    }

    fun decode(content: String): Map<String, String> {
        val pairs = content.split('&').filter(String::isNotBlank).map { token ->
            val separator = token.indexOf('=')
            require(separator > 0) { "Pairing payload contains a malformed field." }
            percentDecode(token.substring(0, separator)) to percentDecode(token.substring(separator + 1))
        }
        require(pairs.map(Pair<String, String>::first).distinct().size == pairs.size) {
            "Duplicate pairing fields are not allowed."
        }
        return pairs.toMap()
    }

    fun required(fields: Map<String, String>, name: String): String =
        fields[name]?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException("Missing $name.")

    private fun percentEncode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val character = unsigned.toChar()
            if (character.isAsciiUnreserved()) {
                append(character)
            } else {
                append('%')
                append(HEX[unsigned ushr 4])
                append(HEX[unsigned and 0x0f])
            }
        }
    }

    private fun percentDecode(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                require(index + 2 < value.length) { "Pairing payload contains truncated percent encoding." }
                val high = value[index + 1].digitToInt(16)
                val low = value[index + 2].digitToInt(16)
                bytes += ((high shl 4) or low).toByte()
                index += 3
            } else {
                val codePoint = value[index].code
                require(codePoint <= 0x7f) { "Non-ASCII pairing text must be percent encoded." }
                bytes += codePoint.toByte()
                index += 1
            }
        }
        return bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    }

    private fun Char.isAsciiUnreserved(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-' || this == '.' || this == '_' || this == '~'
}
