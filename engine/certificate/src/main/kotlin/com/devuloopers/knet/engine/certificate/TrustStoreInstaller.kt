package com.devuloopers.knet.engine.certificate

import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Sealed interface representing the outcome of a Root CA trust store registration attempt.
 */
sealed interface InstallationResult {
    /**
     * Indicates the certificate was successfully registered and trusted by the operating system.
     */
    object Success : InstallationResult

    /**
     * Indicates the registration failed.
     *
     * @property message Descriptive failure reasoning.
     * @property suggestedCommand The shell command the user can execute manually to force trust approval.
     */
    data class Failure(val message: String, val suggestedCommand: String) : InstallationResult
}

/**
 * Utility responsible for automating the installation and verification of KNet's Root CA certificate
 * into host operating system trust stores (macOS Keychain, Windows Root CA store, Linux `ca-certificates`).
 */
object TrustStoreInstaller {

    private const val COMMAND_TIMEOUT_SECONDS = 10L

    /**
     * Attempts to automatically install and trust the given Root CA certificate on the host machine.
     *
     * @param caCertificate The CA certificate to trust.
     * @return An [InstallationResult] indicating success or failure with manual instructions.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun install(caCertificate: X509Certificate): InstallationResult {
        val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        val tempCertFile = File.createTempFile("knet_root_ca", ".crt")

        return try {
            // Write certificate to temporary file in PEM format
            tempCertFile.writer().use { writer ->
                writer.write("-----BEGIN CERTIFICATE-----\n")
                writer.write(Base64.Mime.encode(caCertificate.encoded))
                writer.write("\n-----END CERTIFICATE-----\n")
            }

            when {
                os.contains("win") -> installWindows(tempCertFile)
                os.contains("mac") -> installMac(tempCertFile)
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> installLinux(tempCertFile)
                else -> InstallationResult.Failure(
                    "Unsupported Operating System: $os",
                    "Please manually install the certificate file located at: ${tempCertFile.absolutePath}"
                )
            }
        } finally {
            tempCertFile.delete()
        }
    }

    /**
     * Checks whether the given Root CA is already registered in the host operating system trust store.
     *
     * This allows the UI to detect a previously completed one-time installation and automatically
     * surface a "Trusted" status badge without prompting the user to reinstall on every launch.
     *
     * @param caCertificate The CA certificate whose fingerprint is searched for in the OS store.
     * @return True if the OS trust store contains a matching certificate fingerprint, false otherwise.
     */
    fun isTrusted(caCertificate: X509Certificate): Boolean {
        val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        return try {
            when {
                os.contains("win") -> isTrustedWindows(caCertificate)
                os.contains("mac") -> isTrustedMac(caCertificate)
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> isTrustedLinux(caCertificate)
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a platform trust installation command using [ProcessBuilder], waiting with a timeout,
     * capturing merged output asynchronously to prevent pipe deadlocks, and validating the exit code.
     */
    private fun executeCommand(command: Array<String>, suggestedCommand: String): InstallationResult {
        return try {
            val processBuilder = ProcessBuilder(*command).redirectErrorStream(true)
            val process = processBuilder.start()

            // Asynchronously read merged stdout and stderr to prevent OS pipe buffer deadlocks on large output
            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().readText()
            }

            val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return InstallationResult.Failure(
                    "Command timed out after $COMMAND_TIMEOUT_SECONDS seconds",
                    suggestedCommand
                )
            }

            val output = try {
                outputFuture.get(1, TimeUnit.SECONDS)
            } catch (_: Exception) {
                ""
            }

            val exitCode = process.exitValue()
            if (exitCode == 0) {
                InstallationResult.Success
            } else {
                val errorDetails = output.ifBlank { "Command failed with exit code $exitCode" }
                InstallationResult.Failure(
                    "Command failed with exit code $exitCode: $errorDetails",
                    suggestedCommand
                )
            }
        } catch (e: Exception) {
            InstallationResult.Failure("Failed to execute process: ${e.message}", suggestedCommand)
        }
    }

    /**
     * Windows implementation utilizing command-line certutil for the current user.
     */
    private fun installWindows(certFile: File): InstallationResult {
        val command = arrayOf("certutil", "-user", "-addstore", "Root", certFile.absolutePath)
        val commandString = command.joinToString(" ")
        return executeCommand(command, commandString)
    }

    /**
     * macOS implementation registering in the user's login keychain.
     */
    private fun installMac(certFile: File): InstallationResult {
        // macOS: Install to user's login keychain so it does not prompt for administrator (sudo) privileges.
        val loginKeychain = "${System.getProperty("user.home")}/Library/Keychains/login.keychain-db"
        val command = arrayOf(
            "security", "add-trusted-cert",
            "-d", "-r", "trustRoot",
            "-k", loginKeychain,
            certFile.absolutePath
        )
        val suggestedSudo =
            "sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain \"${certFile.absolutePath}\""
        return executeCommand(command, suggestedSudo)
    }

    /**
     * Linux implementation showing manual instructions for trust commands.
     */
    private fun installLinux(certFile: File): InstallationResult {
        val debianCommand =
            "sudo cp \"${certFile.absolutePath}\" /usr/local/share/ca-certificates/knet_root_ca.crt && sudo update-ca-certificates"
        val rhelCommand =
            "sudo cp \"${certFile.absolutePath}\" /etc/pki/ca-trust/source/anchors/knet_root_ca.crt && sudo update-ca-trust extract"

        return InstallationResult.Failure(
            "Auto-installation is not supported on Linux due to administrative privilege restrictions.",
            "Run one of the following commands in your terminal depending on your distribution:\n" +
                    "Debian/Ubuntu:\n$debianCommand\n\n" +
                    "RHEL/CentOS/Fedora:\n$rhelCommand"
        )
    }

    /**
     * Derives the hex SHA-1 fingerprint of the given certificate without delimiters.
     */
    private fun sha1FingerprintHex(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(cert.encoded)
        return digest.joinToString("") { String.format("%02x", it) }
    }

    /**
     * Derives the hex SHA-256 fingerprint of the given certificate without delimiters.
     */
    private fun sha256FingerprintHex(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString("") { String.format("%02x", it) }
    }

    /**
     * Windows trust detection — runs `certutil` on both the user and system Root stores and
     * matches the certificate's SHA-1 hash or serial number against the output.
     */
    private fun isTrustedWindows(caCertificate: X509Certificate): Boolean {
        val sha1Hex = sha1FingerprintHex(caCertificate).lowercase()
        val serialHex = caCertificate.serialNumber.toString(16).lowercase()

        // Check user Root store first, fallback to machine Root store if needed.
        val userStoreOutput = runCertutilStore("-user", "-store", "Root")
        val machineStoreOutput = runCertutilStore("-store", "Root")
        val combinedOutput = (userStoreOutput + "\n" + machineStoreOutput)
            .lowercase()
            .replace(" ", "")
            .replace(":", "")

        return combinedOutput.contains(sha1Hex) || combinedOutput.contains(serialHex)
    }

    private fun runCertutilStore(vararg args: String): String {
        return try {
            val command = arrayOf("certutil") + args
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            output
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * macOS trust detection — runs `security find-certificate -a -Z` across active keychain search list
     * and checks whether the certificate's SHA-256 fingerprint appears in the output.
     */
    private fun isTrustedMac(caCertificate: X509Certificate): Boolean {
        return try {
            val fingerprint = sha256FingerprintHex(caCertificate).uppercase()
            val process = ProcessBuilder("security", "find-certificate", "-a", "-Z")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val cleanOutput = output.uppercase().replace(" ", "").replace(":", "")
            cleanOutput.contains(fingerprint)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Linux trust detection — scans standard system certificate bundle paths for the CA certificate.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun isTrustedLinux(caCertificate: X509Certificate): Boolean {
        val certBundlePaths = listOf(
            "/etc/ssl/certs/ca-certificates.crt",
            "/etc/pki/tls/certs/ca-bundle.crt",
            "/etc/ssl/ca-bundle.pem",
            "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem",
            "/usr/local/share/ca-certificates/knet_root_ca.crt"
        )
        return try {
            val encodedMime = Base64.Mime.encode(caCertificate.encoded)
            for (path in certBundlePaths) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    if (file.name.contains("knet") || file.readText().contains(encodedMime)) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
