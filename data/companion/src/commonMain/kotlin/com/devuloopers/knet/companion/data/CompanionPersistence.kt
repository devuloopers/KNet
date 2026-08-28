package com.devuloopers.knet.companion.data

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
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
import com.devuloopers.knet.companion.model.CompanionBootstrapPayloadCodec
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

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
            .sortedBy { it.desktopDisplayName.value.lowercase() }
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
                .sortedBy { it.desktopDisplayName.value.lowercase() }
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

    override suspend fun migrateIdentity(
        previousDesktopId: CompanionDesktopId,
        registration: CompanionRegistration,
        makeActive: Boolean,
    ): Boolean = lock.withLock {
        val previous = mutableRegistrations.value.firstOrNull { it.desktopId == previousDesktopId } ?: return@withLock false
        val updated = mutableRegistrations.value
            .filterNot { it.desktopId == previousDesktopId || it.desktopId == registration.desktopId }
            .plus(registration)
            .sortedBy { it.desktopDisplayName.value.lowercase() }
        val activeId = when {
            makeActive -> registration.desktopId
            mutableActiveRegistration.value?.desktopId == previous.desktopId -> registration.desktopId
            else -> mutableActiveRegistration.value?.desktopId
        }
        persist(updated, activeId)
        true
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
        const val SCHEMA_VERSION: Int = 2
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

/** Application adapter for the canonical lightweight version-3 companion bootstrap payload codec. */
public class VersionedCompanionInvitationCodec(
    private val payloadCodec: CompanionBootstrapPayloadCodec = CompanionBootstrapPayloadCodec(),
) : CompanionInvitationCodec {
    override fun decode(payload: String): InvitationDecodeResult = runCatching {
        InvitationDecodeResult.Accepted(payloadCodec.decode(payload))
    }.getOrElse {
        InvitationDecodeResult.Rejected(
            CompanionFailure(
                code = CompanionFailureCode.INVITATION_INVALID,
                message = "Pairing invitation is invalid or unsupported.",
                recoverable = false,
            ),
        )
    }

    /** Encodes [bootstrap] for callers that use the data adapter directly. */
    public fun encode(bootstrap: CompanionPairingBootstrap): String = payloadCodec.encode(bootstrap)
}

/** KNet-owned strict JSON defaults for companion durable records. */
public fun defaultCompanionJson(): Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = false
}

@Serializable
private data class PersistedCompanionEnvelope(
    @SerialName("schema_version") val schemaVersion: Int = 2,
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
    @SerialName("root_certificate_der_base64_url") val rootCertificateDerBase64Url: String,
    @SerialName("credential_reference") val credentialReference: String,
    val scopes: List<String>,
    @SerialName("paired_at_epoch_millis") val pairedAtEpochMillis: Long,
    @SerialName("credential_expires_at_epoch_millis") val credentialExpiresAtEpochMillis: Long,
) {
    fun toDomain(): CompanionRegistration? = runCatching {
        CompanionRegistration(
            desktopId = CompanionDesktopId(desktopId),
            desktopDisplayName = CompanionDesktopDisplayName(desktopDisplayName),
            deviceId = RegisteredDeviceId(deviceId),
            controlEndpoint = CompanionServiceEndpoint(controlHost, controlPort, scheme = CompanionEndpointScheme.HTTPS),
            proxyEndpoint = CompanionServiceEndpoint(proxyHost, proxyPort, scheme = CompanionEndpointScheme.HTTPS),
            transportIdentitySha256 = Sha256Fingerprint(transportIdentitySha256),
            rootCertificateSha256 = Sha256Fingerprint(rootCertificateSha256),
            rootCertificate = CompanionRootCertificate(ROOT_CERTIFICATE_ENCODING.decode(rootCertificateDerBase64Url)),
            credentialReference = CompanionCredentialReference(credentialReference),
            scopes = scopes.map(DeviceScope::valueOf).toSet(),
            pairedAtEpochMillis = pairedAtEpochMillis,
            credentialExpiresAtEpochMillis = credentialExpiresAtEpochMillis,
        )
    }.getOrNull()

    companion object {
        private val ROOT_CERTIFICATE_ENCODING: Base64 =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        fun fromDomain(registration: CompanionRegistration): PersistedCompanionRegistration =
            PersistedCompanionRegistration(
                desktopId = registration.desktopId.value,
                desktopDisplayName = registration.desktopDisplayName.value,
                deviceId = registration.deviceId.value,
                controlHost = registration.controlEndpoint.host,
                controlPort = registration.controlEndpoint.port,
                proxyHost = registration.proxyEndpoint.host,
                proxyPort = registration.proxyEndpoint.port,
                transportIdentitySha256 = registration.transportIdentitySha256.value,
                rootCertificateSha256 = registration.rootCertificateSha256.value,
                rootCertificateDerBase64Url = ROOT_CERTIFICATE_ENCODING.encode(registration.rootCertificate.copyBytes()),
                credentialReference = registration.credentialReference.value,
                scopes = registration.scopes.sortedBy(DeviceScope::name).map(DeviceScope::name),
                pairedAtEpochMillis = registration.pairedAtEpochMillis,
                credentialExpiresAtEpochMillis = registration.credentialExpiresAtEpochMillis,
            )
    }
}
