package com.devuloopers.knet.connectivity.desktop.pairing

import com.devuloopers.knet.application.port.pairing.PairingCryptoPort
import com.devuloopers.knet.application.port.pairing.TrustedDeviceStorePort
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairedDeviceId
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.pairing.TrustedDevice
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/** JCA implementation using CSPRNG, SHA-256, constant-time digest matching, and Ed25519 proof. */
public class JvmPairingCrypto(
    private val random: SecureRandom = SecureRandom(),
) : PairingCryptoPort {
    override fun randomToken(entropyBytes: Int): String {
        require(entropyBytes in 16..128)
        return ByteArray(entropyBytes).also(random::nextBytes).urlEncode()
    }

    override fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).urlEncode()

    override fun constantTimeMatches(value: String, expectedDigest: String): Boolean =
        MessageDigest.isEqual(digest(value).encodeToByteArray(), expectedDigest.encodeToByteArray())

    override fun verifyDeviceProof(
        publicKeyEncoded: String,
        message: String,
        signatureEncoded: String,
    ): Boolean = runCatching {
        val keyBytes = URL_BASE64.decode(publicKeyEncoded)
        val signatureBytes = URL_BASE64.decode(signatureEncoded)
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
        Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(message.encodeToByteArray())
            verify(signatureBytes)
        }
    }.getOrDefault(false)

    private fun ByteArray.urlEncode(): String = URL_BASE64.encode(this)

    private companion object {
        private val URL_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}

/**
 * AES-256-GCM trusted-device store. Its independent key and state files are owner-only where POSIX
 * permissions exist; writes use an atomic replacement and plaintext credentials are never stored.
 */
public class EncryptedFileTrustedDeviceStore(
    private val directory: Path,
    private val random: SecureRandom = SecureRandom(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maximumPendingInvitations: Int = 128,
) : TrustedDeviceStorePort {
    private data class State(
        val invitations: List<PendingPairingInvitation> = emptyList(),
        val devices: List<TrustedDevice> = emptyList(),
    )

    private val mutex = Mutex()
    private val keyPath = directory.resolve("pairing.key")
    private val statePath = directory.resolve("trusted-devices.enc")
    private val key: ByteArray
    private var state: State
    private val devicesFlow: MutableStateFlow<List<TrustedDevice>>

    init {
        require(maximumPendingInvitations in 1..4_096)
        Files.createDirectories(directory)
        secure(directory, isDirectory = true)
        key = loadOrCreateKey()
        state = loadState()
        devicesFlow = MutableStateFlow(state.devices)
    }

    override suspend fun putInvitation(invitation: PendingPairingInvitation): Unit = mutex.withLock {
        val retained = state.invitations
            .filter { it.expiresAtEpochMillis > nowMillis() && it.id != invitation.id }
            .takeLast(maximumPendingInvitations - 1)
        state = state.copy(invitations = retained + invitation)
        persist()
    }

    override suspend fun claimInvitation(
        id: PairingInvitationId,
        secretDigest: String,
        nowEpochMillis: Long,
    ): PendingPairingInvitation? = mutex.withLock {
        val invitation = state.invitations.firstOrNull { it.id == id } ?: return@withLock null
        val digestMatches = MessageDigest.isEqual(
            invitation.secretDigest.encodeToByteArray(),
            secretDigest.encodeToByteArray(),
        )
        if (!digestMatches || nowEpochMillis >= invitation.expiresAtEpochMillis) return@withLock null
        state = state.copy(invitations = state.invitations.filterNot { it.id == id })
        persist()
        invitation
    }

    override suspend fun putDevice(device: TrustedDevice): Unit = mutex.withLock {
        state = state.copy(devices = state.devices.filterNot { it.id == device.id } + device)
        persistAndPublish()
    }

    override suspend fun getDevice(id: PairedDeviceId): TrustedDevice? = mutex.withLock {
        state.devices.firstOrNull { it.id == id }
    }

    override suspend fun revoke(id: PairedDeviceId, revokedAtEpochMillis: Long): Boolean = mutex.withLock {
        val current = state.devices.firstOrNull { it.id == id } ?: return@withLock false
        if (current.isRevoked) return@withLock true
        state = state.copy(
            devices = state.devices.map { if (it.id == id) it.copy(revokedAtEpochMillis = revokedAtEpochMillis) else it },
        )
        persistAndPublish()
        true
    }

    override fun observeDevices(): Flow<List<TrustedDevice>> = devicesFlow.asStateFlow()

    private fun loadOrCreateKey(): ByteArray {
        if (Files.exists(keyPath)) return Files.readAllBytes(keyPath).also { require(it.size == KEY_BYTES) }
        val created = ByteArray(KEY_BYTES).also(random::nextBytes)
        val temporary = Files.createTempFile(directory, "pairing-key-", ".tmp")
        Files.write(temporary, created)
        secure(temporary, isDirectory = false)
        try {
            Files.move(temporary, keyPath, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            Files.deleteIfExists(temporary)
            return Files.readAllBytes(keyPath).also { require(it.size == KEY_BYTES) }
        }
        secure(keyPath, isDirectory = false)
        return created
    }

    private fun loadState(): State {
        if (!Files.exists(statePath)) return State()
        val encoded = Files.readAllBytes(statePath)
        require(encoded.size > NONCE_BYTES) { "Pairing store is corrupt." }
        val nonce = encoded.copyOfRange(0, NONCE_BYTES)
        val cipherText = encoded.copyOfRange(NONCE_BYTES, encoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return decode(cipher.doFinal(cipherText).decodeToString())
    }

    private fun persistAndPublish() {
        persist()
        devicesFlow.value = state.devices
    }

    private fun persist() {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(encode(state).encodeToByteArray())
        val temporary = Files.createTempFile(directory, "trusted-devices-", ".tmp")
        Files.write(temporary, nonce + encrypted)
        secure(temporary, isDirectory = false)
        try {
            Files.move(
                temporary,
                statePath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
        secure(statePath, isDirectory = false)
    }

    private fun encode(state: State): String = buildJsonObject {
        put("version", JsonPrimitive(1))
        put("invitations", buildJsonArray { state.invitations.forEach { add(it.toJson()) } })
        put("devices", buildJsonArray { state.devices.forEach { add(it.toJson()) } })
    }.toString()

    private fun decode(encoded: String): State {
        val root = json.parseToJsonElement(encoded).jsonObject
        require(root["version"]?.jsonPrimitive?.content == "1") { "Pairing store version is unsupported." }
        return State(
            invitations = root["invitations"]?.jsonArray.orEmpty().map { it.jsonObject.toInvitation() },
            devices = root["devices"]?.jsonArray.orEmpty().map { it.jsonObject.toDevice() },
        )
    }

    private fun PendingPairingInvitation.toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id.value)); put("digest", JsonPrimitive(secretDigest))
        put("expires", JsonPrimitive(expiresAtEpochMillis)); put("created", JsonPrimitive(createdAtEpochMillis))
        put("scopes", buildJsonArray { scopes.sortedBy(DeviceScope::name).forEach { add(JsonPrimitive(it.name)) } })
    }

    private fun TrustedDevice.toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id.value)); put("name", JsonPrimitive(displayName))
        put("publicKey", JsonPrimitive(publicKeyEncoded)); put("digest", JsonPrimitive(credentialDigest))
        put("paired", JsonPrimitive(pairedAtEpochMillis)); put("expires", JsonPrimitive(credentialExpiresAtEpochMillis))
        revokedAtEpochMillis?.let { put("revoked", JsonPrimitive(it)) }
        put("scopes", buildJsonArray { scopes.sortedBy(DeviceScope::name).forEach { add(JsonPrimitive(it.name)) } })
    }

    private fun JsonObject.toInvitation(): PendingPairingInvitation = PendingPairingInvitation(
        PairingInvitationId(text("id")), text("digest"), long("expires"), scopes(), long("created"),
    )

    private fun JsonObject.toDevice(): TrustedDevice = TrustedDevice(
        PairedDeviceId(text("id")), text("name"), text("publicKey"), text("digest"), scopes(),
        long("paired"), long("expires"), get("revoked")?.jsonPrimitive?.content?.toLongOrNull(),
    )

    private fun JsonObject.text(name: String): String = get(name)?.jsonPrimitive?.content ?: error("Missing $name")
    private fun JsonObject.long(name: String): Long = text(name).toLong()
    private fun JsonObject.scopes(): Set<DeviceScope> = get("scopes")?.jsonArray.orEmpty()
        .map { DeviceScope.valueOf(it.jsonPrimitive.content) }.toSet()

    private fun secure(path: Path, isDirectory: Boolean) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                if (isDirectory) setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ) else setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    private companion object {
        private const val KEY_BYTES: Int = 32
        private const val NONCE_BYTES: Int = 12
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
