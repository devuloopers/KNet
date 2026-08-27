package com.devuloopers.knet.data.desktop.identity

import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.engine.certificate.CertificateFileSecurity
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/** Stable installation identity plus authenticated aliases accepted only during migration. */
data class DesktopInstallationIdentity(
    val canonicalId: CompanionDesktopId,
    val legacyIds: Set<CompanionDesktopId>,
) {
    init {
        require(canonicalId !in legacyIds) { "Canonical desktop identity must not be repeated as a legacy alias." }
        require(legacyIds.size <= MAXIMUM_LEGACY_IDENTITIES) { "Too many legacy desktop identity aliases." }
    }

    private companion object {
        const val MAXIMUM_LEGACY_IDENTITIES: Int = 4
    }
}

/**
 * Owner-only persistent UUID for one KNet desktop installation.
 *
 * The UUID is deliberately independent from the Root CA. Certificate rotation can therefore be reported as an
 * identity change without making the known desktop disappear. The caller supplies certificate-derived legacy IDs
 * only as authenticated migration aliases; they are never used as the canonical identity of a new installation.
 */
class DesktopInstallationIdentityRepository(
    baseDirectory: File,
    private val uuidFactory: () -> Uuid = Uuid::random,
) {
    private val identityDirectory: File = File(baseDirectory, IDENTITY_DIRECTORY_NAME).also { directory ->
        check(CertificateFileSecurity.secureDirectory(directory)) {
            "Unable to secure desktop identity directory '${directory.absolutePath}'."
        }
    }
    private val identityFile: File = File(identityDirectory, IDENTITY_FILE_NAME)

    /** Loads the existing installation UUID or creates and atomically persists one. */
    @Synchronized
    fun loadOrCreate(legacyIds: Set<CompanionDesktopId> = emptySet()): DesktopInstallationIdentity {
        val canonicalId = if (identityFile.isFile) {
            check(CertificateFileSecurity.secureSecretFile(identityFile)) {
                "Unable to secure persisted desktop identity '${identityFile.absolutePath}'."
            }
            parse(identityFile.readText())
        } else {
            createIdentity()
        }
        return DesktopInstallationIdentity(
            canonicalId = canonicalId,
            legacyIds = legacyIds.filterNotTo(linkedSetOf()) { it == canonicalId },
        )
    }

    private fun createIdentity(): CompanionDesktopId {
        val generated = CompanionDesktopId(uuidFactory().toString().lowercase())
        val temporary = File(identityDirectory, "$IDENTITY_FILE_NAME.tmp-${Uuid.random()}")
        try {
            temporary.writeText(generated.value)
            check(CertificateFileSecurity.secureSecretFile(temporary)) {
                "Unable to secure temporary desktop identity material."
            }
            try {
                try {
                    Files.move(
                        temporary.toPath(),
                        identityFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), identityFile.toPath())
                }
            } catch (_: FileAlreadyExistsException) {
                check(CertificateFileSecurity.secureSecretFile(identityFile)) {
                    "Unable to secure concurrently persisted desktop identity '${identityFile.absolutePath}'."
                }
                return parse(identityFile.readText())
            }
            check(CertificateFileSecurity.secureSecretFile(identityFile)) {
                "Unable to secure persisted desktop identity '${identityFile.absolutePath}'."
            }
            return generated
        } finally {
            temporary.delete()
        }
    }

    private fun parse(content: String): CompanionDesktopId {
        val normalized = content.trim()
        val uuid = runCatching { Uuid.parse(normalized) }.getOrNull()
            ?: throw IllegalStateException("Persisted desktop identity is invalid.")
        require(uuid.toString() == normalized.lowercase()) { "Persisted desktop identity is not canonical." }
        return CompanionDesktopId(uuid.toString())
    }

    private companion object {
        const val IDENTITY_DIRECTORY_NAME: String = "identity"
        const val IDENTITY_FILE_NAME: String = "desktop-id"
    }
}
