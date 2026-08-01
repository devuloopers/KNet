package com.devuloopers.knet.engine.certificate

import java.io.File
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

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
 * Utility service to automatically register KNet's Root CA certificate into the host OS trust store.
 *
 * **Threading Contract**: Trust installation remains a synchronous operation. Callers are responsible for
 * invoking it from an appropriate background thread (e.g. `Dispatchers.IO`) if they wish to avoid blocking the UI.
 */
object TrustStoreInstaller {

    private const val COMMAND_TIMEOUT_SECONDS = 5L

    /**
     * Installs the given Root CA certificate into the host operating system's trust store.
     * Detects the OS type and runs platform-specific utilities.
     *
     * @param caCertificate The CA certificate to trust.
     * @return An [InstallationResult] indicating success or failure with manual instructions.
     */
    fun install(caCertificate: X509Certificate): InstallationResult {
        val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        val tempCertFile = File.createTempFile("knet_root_ca", ".crt")
        tempCertFile.deleteOnExit()

        // Write certificate to temporary file in PEM format
        tempCertFile.writer().use { writer ->
            writer.write("-----BEGIN CERTIFICATE-----\n")
            writer.write(Base64.getMimeEncoder().encodeToString(caCertificate.encoded))
            writer.write("\n-----END CERTIFICATE-----\n")
        }

        return when {
            os.contains("win") -> installWindows(tempCertFile)
            os.contains("mac") -> installMac(tempCertFile)
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> installLinux(tempCertFile)
            else -> InstallationResult.Failure(
                "Unsupported Operating System: $os",
                "Please manually install the certificate file located at: ${tempCertFile.absolutePath}"
            )
        }
    }

    /**
     * Executes a platform trust installation command using [ProcessBuilder], waiting with a timeout,
     * capturing stdout/stderr streams, and validating the exit code.
     */
    private fun executeCommand(command: Array<String>, suggestedCommand: String): InstallationResult {
        return try {
            val processBuilder = ProcessBuilder(*command)
            val process = processBuilder.start()

            val stdoutFuture = process.inputStream.bufferedReader().readText()
            val stderrFuture = process.errorStream.bufferedReader().readText()

            val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return InstallationResult.Failure(
                    "Command timed out after $COMMAND_TIMEOUT_SECONDS seconds",
                    suggestedCommand
                )
            }

            val exitCode = process.exitValue()
            if (exitCode == 0) {
                InstallationResult.Success
            } else {
                val errorDetails = stderrFuture.ifBlank { stdoutFuture }
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
     * Windows implementation utilizing command-line certutil.
     */
    private fun installWindows(certFile: File): InstallationResult {
        val command = arrayOf("certutil", "-addstore", "-user", "ROOT", certFile.absolutePath)
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
        val suggestedSudo = "sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain \"${certFile.absolutePath}\""
        return executeCommand(command, suggestedSudo)
    }

    /**
     * Linux implementation showing manual instructions for trust commands.
     */
    private fun installLinux(certFile: File): InstallationResult {
        val debianCommand = "sudo cp \"${certFile.absolutePath}\" /usr/local/share/ca-certificates/knet_root_ca.crt && sudo update-ca-certificates"
        val rhelCommand = "sudo cp \"${certFile.absolutePath}\" /etc/pki/ca-trust/source/anchors/knet_root_ca.crt && sudo update-ca-trust extract"

        return InstallationResult.Failure(
            "Auto-installation is not supported on Linux due to administrative privilege restrictions.",
            "Run one of the following commands in your terminal depending on your distribution:\n" +
                    "Debian/Ubuntu:\n$debianCommand\n\n" +
                    "RHEL/CentOS/Fedora:\n$rhelCommand"
        )
    }
}
