package com.devuloopers.knet.engine.certificate

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

/**
 * Applies owner-only filesystem policy to certificate material and derives opaque internal names.
 *
 * POSIX permissions are used when available. The Java owner flags are also applied as a portable
 * best effort for filesystems that do not expose POSIX attributes.
 */
object CertificateFileSecurity {

    /**
     * Creates [directory] when necessary and restricts it to its owning user.
     *
     * @param directory Directory containing certificate or private-key material.
     * @return `true` when the directory exists after the operation.
     */
    fun secureDirectory(directory: File): Boolean {
        if (!directory.exists() && !directory.mkdirs()) return false
        val portableSecured = applyPortableOwnerFlags(directory, executable = true)
        val posixSecured = try {
            Files.setPosixFilePermissions(
                directory.toPath(),
                PosixFilePermissions.fromString("rwx------"),
            )
            true
        } catch (_: UnsupportedOperationException) {
            true
        } catch (_: Exception) {
            false
        }
        return directory.isDirectory && portableSecured && posixSecured
    }

    /**
     * Restricts an existing secret file to owner read/write access.
     *
     * @param file Certificate, key, credential, or metadata file to secure.
     * @return `true` when the file exists after applying the policy.
     */
    fun secureSecretFile(file: File): Boolean {
        if (!file.exists()) return false
        val portableSecured = applyPortableOwnerFlags(file, executable = false)
        val posixSecured = try {
            Files.setPosixFilePermissions(
                file.toPath(),
                PosixFilePermissions.fromString("rw-------"),
            )
            true
        } catch (_: UnsupportedOperationException) {
            true
        } catch (_: Exception) {
            false
        }
        return file.isFile && portableSecured && posixSecured
    }

    /**
     * Produces an opaque filename for an imported identity without embedding a user-controlled alias.
     *
     * @param alias User-visible certificate alias.
     * @param sourceExtension Extension of the imported source file.
     * @return SHA-256-derived filename with a conservative extension.
     * @throws IllegalArgumentException When [alias] is blank.
     */
    fun opaqueIdentityFileName(alias: String, sourceExtension: String): String {
        require(alias.isNotBlank()) { "Certificate alias must not be blank." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(alias.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        val extension = sourceExtension
            .lowercase()
            .filter { character -> character.isLetterOrDigit() }
            .take(10)
            .ifBlank { "bin" }
        return "$digest.$extension"
    }

    /**
     * Produces a readable export filename that cannot escape the selected destination directory.
     *
     * @param alias User-visible certificate alias.
     * @param sourceExtension Extension of the stored identity file.
     * @return Conservative filename suitable for a user-selected export directory.
     */
    fun exportIdentityFileName(alias: String, sourceExtension: String): String {
        val stem = alias
            .trim()
            .map { character ->
                when {
                    character.isLetterOrDigit() -> character
                    character == '-' || character == '_' -> character
                    else -> '_'
                }
            }
            .joinToString(separator = "")
            .trim('_')
            .take(64)
            .ifBlank { "client-certificate" }
        val extension = sourceExtension
            .lowercase()
            .filter { character -> character.isLetterOrDigit() }
            .take(10)
            .ifBlank { "bin" }
        return "$stem.$extension"
    }

    /** Clears broad Java permission flags before granting only owner access. */
    private fun applyPortableOwnerFlags(file: File, executable: Boolean): Boolean {
        val results = listOf(
            file.setReadable(false, false),
            file.setWritable(false, false),
            file.setExecutable(false, false),
            file.setReadable(true, true),
            file.setWritable(true, true),
            !executable || file.setExecutable(true, true),
        )
        return results.all { it }
    }
}
