package com.devuloopers.knet.companion.data

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.InvitationDecodeResult
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned durable registration repository. Invalid or future-version records fail closed to an empty state. */
public class VersionedCompanionRegistrationRepository(
    private val store: CompanionRecordStore,
    private val json: Json = defaultCompanionJson(),
) : CompanionRegistrationRepository {
    private val lock: Mutex = Mutex()
    private val mutableRegistrations: MutableStateFlow<List<CompanionRegistration>>
    private val mutableActiveRegistration: MutableStateFlow<CompanionRegistration?>

    override val registrations: StateFlow<List<CompanionRegistration>>
    override val activeRegistration: StateFlow<CompanionRegistration?>

    init {
        val restored = store.content.value?.let(::decodeEnvelope) ?: PersistedCompanionEnvelope()
        val domain = restored.registrations.mapNotNull(PersistedCompanionRegistration::toDomain)
            .distinctBy { it.desktopId }
            .sortedBy { it.desktopDisplayName.lowercase() }
        mutableRegistrations = MutableStateFlow(domain)
        mutableActiveRegistration = MutableStateFlow(
            domain.firstOrNull { it.desktopId.value == restored.activeDesktopId },
        )
        registrations = mutableRegistrations.asStateFlow()
        activeRegistration = mutableActiveRegistration.asStateFlow()
    }

    override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) {
        lock.withLock {
            val updated = (mutableRegistrations.value.filterNot { it.desktopId == registration.desktopId } + registration)
                .sortedBy { it.desktopDisplayName.lowercase() }
            val active = if (makeActive) registration else mutableActiveRegistration.value
            persist(updated, active?.desktopId)
        }
    }

    override suspend fun setActive(desktopId: CompanionDesktopId?): Boolean = lock.withLock {
        val selected = desktopId?.let { id -> mutableRegistrations.value.firstOrNull { it.desktopId == id } }
        if (desktopId != null && selected == null) return@withLock false
        persist(mutableRegistrations.value, selected?.desktopId)
        true
    }

    override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? = lock.withLock {
        val removed = mutableRegistrations.value.firstOrNull { it.desktopId == desktopId } ?: return@withLock null
        val remaining = mutableRegistrations.value.filterNot { it.desktopId == desktopId }
        val nextActive = mutableActiveRegistration.value
            ?.takeUnless { it.desktopId == desktopId }
            ?: remaining.firstOrNull()
        persist(remaining, nextActive?.desktopId)
        removed
    }

    private suspend fun persist(
        updated: List<CompanionRegistration>,
        activeDesktopId: CompanionDesktopId?,
    ) {
        val envelope = PersistedCompanionEnvelope(
            activeDesktopId = activeDesktopId?.value,
            registrations = updated.map(PersistedCompanionRegistration::fromDomain),
        )
        store.write(json.encodeToString(envelope))
        mutableRegistrations.value = updated
        mutableActiveRegistration.value = updated.firstOrNull { it.desktopId == activeDesktopId }
    }

    private fun decodeEnvelope(content: String): PersistedCompanionEnvelope = runCatching {
        json.decodeFromString<PersistedCompanionEnvelope>(content).takeIf { it.schemaVersion == SCHEMA_VERSION }
    }.getOrNull() ?: PersistedCompanionEnvelope()

    private companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

/** Credential adapter that keeps application contracts independent from platform secure-store APIs. */
public class ProtectedCompanionCredentialStore(
    private val secrets: CompanionSecretStore,
) : CompanionCredentialStore {
    override suspend fun write(reference: CompanionCredentialReference, credential: String) {
        require(credential.isNotBlank())
        secrets.write(reference.value, credential)
    }

    override suspend fun read(reference: CompanionCredentialReference): String? = secrets.read(reference.value)

    override suspend fun remove(reference: CompanionCredentialReference): Unit = secrets.remove(reference.value)
}

/** Strict `knet://pair/v1` invitation codec shared by QR, deep-link, and paste entry points. */
public class VersionedCompanionInvitationCodec : CompanionInvitationCodec {
    override fun decode(payload: String): InvitationDecodeResult = runCatching {
        require(payload.length <= MAXIMUM_INVITATION_CHARACTERS) { "Pairing invitation is too large." }
        val query = payload.removePrefix(PREFIX).takeIf { payload.startsWith(PREFIX) }
            ?: error("Unsupported pairing invitation scheme or version.")
        val fields = parseQuery(query)
        require(fields.keys == EXPECTED_FIELDS) { "Pairing invitation fields do not match protocol version 1." }
        val scopes = required(fields, "scopes").split(',').filter(String::isNotBlank).map(DeviceScope::valueOf).toSet()
        val invitation = CompanionPairingInvitation(
            protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
            desktopId = CompanionDesktopId(required(fields, "desktopId")),
            desktopDisplayName = required(fields, "desktopName"),
            pairing = PairingInvitation(
                id = PairingInvitationId(required(fields, "id")),
                secret = required(fields, "secret"),
                expiresAtEpochMillis = required(fields, "expires").toLong(),
                scopes = scopes,
            ),
            controlEndpoint = CompanionServiceEndpoint(
                host = required(fields, "controlHost"),
                port = required(fields, "controlPort").toInt(),
                secure = true,
            ),
            proxyEndpoint = CompanionServiceEndpoint(
                host = required(fields, "proxyHost"),
                port = required(fields, "proxyPort").toInt(),
                secure = true,
            ),
            transportIdentitySha256 = Sha256Fingerprint(required(fields, "transportPin")),
            rootCertificateSha256 = Sha256Fingerprint(required(fields, "rootCa")),
        )
        InvitationDecodeResult.Accepted(invitation)
    }.getOrElse {
        InvitationDecodeResult.Rejected(
            CompanionFailure(
                code = CompanionFailureCode.INVITATION_INVALID,
                message = "Pairing invitation is invalid or unsupported.",
                recoverable = false,
            ),
        )
    }

    /** Encodes the same canonical payload used by a future desktop companion onboarding surface. */
    public fun encode(invitation: CompanionPairingInvitation): String = buildString {
        append(PREFIX)
        append(
            listOf(
                "desktopId" to invitation.desktopId.value,
                "desktopName" to invitation.desktopDisplayName,
                "id" to invitation.pairing.id.value,
                "secret" to invitation.pairing.secret,
                "expires" to invitation.pairing.expiresAtEpochMillis.toString(),
                "scopes" to invitation.pairing.scopes.sortedBy(DeviceScope::name).joinToString(",", transform = DeviceScope::name),
                "controlHost" to invitation.controlEndpoint.host,
                "controlPort" to invitation.controlEndpoint.port.toString(),
                "proxyHost" to invitation.proxyEndpoint.host,
                "proxyPort" to invitation.proxyEndpoint.port.toString(),
                "transportPin" to invitation.transportIdentitySha256.value,
                "rootCa" to invitation.rootCertificateSha256.value,
            ).joinToString("&") { (key, value) -> "$key=${percentEncode(value)}" },
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        val pairs = query.split('&').filter(String::isNotBlank).map { token ->
            val separator = token.indexOf('=')
            require(separator > 0)
            percentDecode(token.substring(0, separator)) to percentDecode(token.substring(separator + 1))
        }
        require(pairs.map(Pair<String, String>::first).distinct().size == pairs.size) {
            "Duplicate invitation fields are not allowed."
        }
        return pairs.toMap()
    }

    private fun required(fields: Map<String, String>, name: String): String =
        fields[name]?.takeIf(String::isNotBlank) ?: error("Missing $name.")

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
                require(index + 2 < value.length)
                val high = value[index + 1].digitToInt(16)
                val low = value[index + 2].digitToInt(16)
                bytes += ((high shl 4) or low).toByte()
                index += 3
            } else {
                val codePoint = value[index].code
                require(codePoint <= 0x7f) { "Non-ASCII invitation text must be percent encoded." }
                bytes += codePoint.toByte()
                index += 1
            }
        }
        return bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    }

    private fun Char.isAsciiUnreserved(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-' || this == '.' || this == '_' || this == '~'

    private companion object {
        const val PREFIX: String = "knet://pair/v1?"
        const val HEX: String = "0123456789ABCDEF"
        const val MAXIMUM_INVITATION_CHARACTERS: Int = 8 * 1024
        val EXPECTED_FIELDS: Set<String> = setOf(
            "desktopId",
            "desktopName",
            "id",
            "secret",
            "expires",
            "scopes",
            "controlHost",
            "controlPort",
            "proxyHost",
            "proxyPort",
            "transportPin",
            "rootCa",
        )
    }
}

/** KNet-owned strict JSON defaults for companion durable records. */
public fun defaultCompanionJson(): Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = false
}

@Serializable
private data class PersistedCompanionEnvelope(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("active_desktop_id") val activeDesktopId: String? = null,
    val registrations: List<PersistedCompanionRegistration> = emptyList(),
)

@Serializable
private data class PersistedCompanionRegistration(
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("desktop_display_name") val desktopDisplayName: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("control_host") val controlHost: String,
    @SerialName("control_port") val controlPort: Int,
    @SerialName("proxy_host") val proxyHost: String,
    @SerialName("proxy_port") val proxyPort: Int,
    @SerialName("transport_identity_sha256") val transportIdentitySha256: String,
    @SerialName("root_certificate_sha256") val rootCertificateSha256: String,
    @SerialName("credential_reference") val credentialReference: String,
    val scopes: List<String>,
    @SerialName("paired_at_epoch_millis") val pairedAtEpochMillis: Long,
    @SerialName("credential_expires_at_epoch_millis") val credentialExpiresAtEpochMillis: Long,
) {
    fun toDomain(): CompanionRegistration? = runCatching {
        CompanionRegistration(
            desktopId = CompanionDesktopId(desktopId),
            desktopDisplayName = desktopDisplayName,
            deviceId = RegisteredDeviceId(deviceId),
            controlEndpoint = CompanionServiceEndpoint(controlHost, controlPort, secure = true),
            proxyEndpoint = CompanionServiceEndpoint(proxyHost, proxyPort, secure = true),
            transportIdentitySha256 = Sha256Fingerprint(transportIdentitySha256),
            rootCertificateSha256 = Sha256Fingerprint(rootCertificateSha256),
            credentialReference = CompanionCredentialReference(credentialReference),
            scopes = scopes.map(DeviceScope::valueOf).toSet(),
            pairedAtEpochMillis = pairedAtEpochMillis,
            credentialExpiresAtEpochMillis = credentialExpiresAtEpochMillis,
        )
    }.getOrNull()

    companion object {
        fun fromDomain(registration: CompanionRegistration): PersistedCompanionRegistration =
            PersistedCompanionRegistration(
                desktopId = registration.desktopId.value,
                desktopDisplayName = registration.desktopDisplayName,
                deviceId = registration.deviceId.value,
                controlHost = registration.controlEndpoint.host,
                controlPort = registration.controlEndpoint.port,
                proxyHost = registration.proxyEndpoint.host,
                proxyPort = registration.proxyEndpoint.port,
                transportIdentitySha256 = registration.transportIdentitySha256.value,
                rootCertificateSha256 = registration.rootCertificateSha256.value,
                credentialReference = registration.credentialReference.value,
                scopes = registration.scopes.sortedBy(DeviceScope::name).map(DeviceScope::name),
                pairedAtEpochMillis = registration.pairedAtEpochMillis,
                credentialExpiresAtEpochMillis = registration.credentialExpiresAtEpochMillis,
            )
    }
}
