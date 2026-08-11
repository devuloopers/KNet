package com.devuloopers.knet.engine.certificate

import com.devuloopers.knet.core.logger.KNetLogger
import java.io.File
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val TAG = "IosSimctlInstaller"

/**
 * Result outcome of an iOS `simctl` CLI command execution.
 */
sealed interface SimctlResult {
    /** Indicates successful certificate injection into booted simulator keychain. */
    data class Success(val message: String) : SimctlResult

    /** Indicates failure during command execution. */
    data class Failure(val error: String) : SimctlResult
}

/**
 * Utility for automating iOS Simulator certificate trust store injection using macOS `xcrun simctl`.
 */
object IosSimctlInstaller {

    private const val COMMAND_TIMEOUT_SECONDS = 5L

    /**
     * Detects if any iOS Simulator is currently booted.
     *
     * @return True if a booted iOS Simulator is found.
     */
    fun hasBootedSimulator(): Boolean {
        return try {
            val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "booted").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            output.contains("(Booted)")
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to check booted iOS Simulators: ${e.message}" }
            false
        }
    }

    /**
     * Injects KNet's Root CA certificate into the keychain of all booted iOS Simulators.
     *
     * @param caCertificate The [X509Certificate] instance of KNet's Root CA.
     * @return A [SimctlResult] indicating success or failure.
     */
    fun injectCertificate(caCertificate: X509Certificate): SimctlResult {
        val tempCertFile = File.createTempFile("knet_root_ca_ios", ".pem")
        tempCertFile.deleteOnExit()

        return try {
            tempCertFile.writer().use { writer ->
                writer.write("-----BEGIN CERTIFICATE-----\n")
                writer.write(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(caCertificate.encoded))
                writer.write("\n-----END CERTIFICATE-----\n")
            }

            val command = arrayOf("xcrun", "simctl", "keychain", "booted", "add-cert", tempCertFile.absolutePath)
            val process = ProcessBuilder(*command).start()
            val stderr = process.errorStream.bufferedReader().readText()
            val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (completed && process.exitValue() == 0) {
                val msg = "[SIMCTL] Successfully injected Root CA certificate into booted iOS Simulator keychain."
                KNetLogger.info(TAG) { msg }
                SimctlResult.Success(msg)
            } else {
                val err = stderr.ifEmpty { "xcrun simctl exited with code ${process.exitValue()}" }
                KNetLogger.error(TAG) { "Simctl Injection Failed: $err" }
                SimctlResult.Failure(err)
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Simctl Injection Exception: ${e.message}" }
            SimctlResult.Failure(e.message ?: "Failed to execute xcrun simctl command")
        } finally {
            tempCertFile.delete()
        }
    }
}
