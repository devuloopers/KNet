package com.devuloopers.knet.engine.certificate

import com.devuloopers.knet.core.logger.KNetLogger
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private const val TAG = "AndroidAdbInstaller"

/**
 * Result outcome of an ADB command execution.
 */
sealed interface AdbResult {
    /** Indicates successful command execution. */
    data class Success(val message: String) : AdbResult

    /** Indicates failure during command execution. */
    data class Failure(val error: String) : AdbResult
}

/**
 * Utility for automating Android device and emulator proxy configuration using the `adb` CLI tool.
 */
object AndroidAdbInstaller {

    private val COMMAND_TIMEOUT = 5.seconds

    /**
     * Lists currently connected Android devices and emulators.
     *
     * @return List of active device identifiers.
     */
    fun getConnectedDevices(): List<String> {
        return try {
            val process = ProcessBuilder("adb", "devices").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(COMMAND_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)

            output.lines()
                .drop(1) // Skip "List of devices attached" header
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains("device") }
                .map { it.split("\\s+".toRegex())[0] }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to list connected ADB devices: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Configures the global HTTP proxy setting on connected Android devices via ADB.
     *
     * @param hostIp The LAN IP address of the KNet Desktop host machine.
     * @param port The active HTTP proxy port (default: 8080).
     * @param deviceId Optional targeted device ID. If null, applies to default device.
     * @return An [AdbResult] indicating success or failure.
     */
    fun configureProxy(hostIp: String, port: Int = 8080, deviceId: String? = null): AdbResult {
        val command = mutableListOf("adb")
        if (deviceId != null) {
            command.addAll(listOf("-s", deviceId))
        }
        command.addAll(listOf("shell", "settings", "put", "global", "http_proxy", "$hostIp:$port"))

        return executeCommand(command.toTypedArray(), "[ADB] Successfully configured HTTP proxy $hostIp:$port on Android device.")
    }

    /**
     * Clears the global HTTP proxy setting on connected Android devices via ADB.
     *
     * @param deviceId Optional targeted device ID. If null, applies to default device.
     * @return An [AdbResult] indicating success or failure.
     */
    fun clearProxy(deviceId: String? = null): AdbResult {
        val command = mutableListOf("adb")
        if (deviceId != null) {
            command.addAll(listOf("-s", deviceId))
        }
        command.addAll(listOf("shell", "settings", "delete", "global", "http_proxy"))

        return executeCommand(command.toTypedArray(), "[ADB] Successfully cleared HTTP proxy setting on Android device.")
    }

    private fun executeCommand(command: Array<String>, successMessage: String): AdbResult {
        return try {
            val process = ProcessBuilder(*command).start()
            val stderr = process.errorStream.bufferedReader().readText()
            val completed = process.waitFor(COMMAND_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)

            if (completed && process.exitValue() == 0) {
                KNetLogger.info(TAG) { successMessage }
                AdbResult.Success(successMessage)
            } else {
                val err = stderr.ifEmpty { "Command exited with code ${process.exitValue()}" }
                KNetLogger.error(TAG) { "ADB Command Failed: $err" }
                AdbResult.Failure(err)
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "ADB Execution Exception: ${e.message}" }
            AdbResult.Failure(e.message ?: "Failed to execute ADB command")
        }
    }
}
