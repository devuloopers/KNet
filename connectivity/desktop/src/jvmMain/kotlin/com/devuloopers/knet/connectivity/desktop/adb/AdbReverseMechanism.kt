package com.devuloopers.knet.connectivity.desktop.adb

import kotlin.time.Clock
import com.devuloopers.knet.connectivity.model.ConnectivityAvailability
import com.devuloopers.knet.connectivity.model.ConnectivityCapability
import com.devuloopers.knet.connectivity.model.ConnectivityHealth
import com.devuloopers.knet.connectivity.model.ConnectivityLifecycle
import com.devuloopers.knet.connectivity.model.ConnectivityMechanismId
import com.devuloopers.knet.connectivity.spi.ActivationRequest
import com.devuloopers.knet.connectivity.spi.ActivationResult
import com.devuloopers.knet.connectivity.spi.DeactivationReason
import com.devuloopers.knet.connectivity.spi.DeactivationResult
import com.devuloopers.knet.connectivity.spi.ManagedConnectivityMechanism
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Structured command result; stdout is bounded and never interpreted as shell syntax. */
public data class ProcessCommandResult(public val exitCode: Int, public val output: String)

public fun interface ProcessCommandPort {
    public suspend fun execute(arguments: List<String>, timeoutMillis: Long): ProcessCommandResult
}

/** Shell-free JVM process adapter with bounded output and deadline. */
public class JvmProcessCommandAdapter : ProcessCommandPort {
    override suspend fun execute(arguments: List<String>, timeoutMillis: Long): ProcessCommandResult =
        withContext(Dispatchers.IO) {
            require(arguments.isNotEmpty() && arguments.all { '\u0000' !in it })
            val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
            val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@withContext ProcessCommandResult(-1, "process_timeout")
            }
            val output = process.inputStream.bufferedReader().use { it.readText().take(MAX_OUTPUT_CHARS) }
            ProcessCommandResult(process.exitValue(), output)
        }

    private companion object {
        private const val MAX_OUTPUT_CHARS: Int = 16_384
    }
}

/** Independently activated ADB reverse mapping; it never changes proxy listener state. */
public class AdbReverseMechanism(
    private val proxyPort: () -> Int?,
    private val commands: ProcessCommandPort = JvmProcessCommandAdapter(),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ManagedConnectivityMechanism {
    override val id: ConnectivityMechanismId = ConnectivityMechanismId("adb-reverse")
    override val capabilities: Set<ConnectivityCapability> = setOf(
        ConnectivityCapability.DEVICE_COMMAND,
        ConnectivityCapability.REQUIRES_ACTIVATION,
    )
    override val availability: Flow<ConnectivityAvailability>
        get() = flowOf(
            proxyPort()?.let { ConnectivityAvailability.Available }
                ?: ConnectivityAvailability.NetworkUnavailable("proxy_endpoint_missing"),
        )
    private val mutableLifecycle = MutableStateFlow<ConnectivityLifecycle>(ConnectivityLifecycle.Inactive)
    override val lifecycle: StateFlow<ConnectivityLifecycle> = mutableLifecycle.asStateFlow()
    private val mutableHealth = MutableStateFlow<ConnectivityHealth>(ConnectivityHealth.Unknown)
    override val health: StateFlow<ConnectivityHealth> = mutableHealth.asStateFlow()
    private val mutex = Mutex()

    override suspend fun activate(request: ActivationRequest): ActivationResult = mutex.withLock {
        val port = proxyPort() ?: return@withLock ActivationResult.Rejected("proxy_endpoint_missing")
        if (mutableLifecycle.value is ConnectivityLifecycle.Active) return@withLock ActivationResult.Accepted
        mutableLifecycle.value = ConnectivityLifecycle.Activating
        val result = execute(listOf("adb", "reverse", "tcp:$port", "tcp:$port"))
        if (result.exitCode == 0) {
            mutableLifecycle.value = ConnectivityLifecycle.Active("adb-reverse-$port")
            mutableHealth.value = ConnectivityHealth.Healthy(nowMillis())
            ActivationResult.Accepted
        } else {
            mutableLifecycle.value = ConnectivityLifecycle.Failed("adb_reverse_failed", recoverable = true)
            mutableHealth.value = ConnectivityHealth.Unreachable("adb_command_failed")
            ActivationResult.Rejected("adb_reverse_failed")
        }
    }

    override suspend fun deactivate(reason: DeactivationReason): DeactivationResult = mutex.withLock {
        val port = proxyPort()
        if (mutableLifecycle.value is ConnectivityLifecycle.Inactive || port == null) {
            mutableLifecycle.value = ConnectivityLifecycle.Inactive
            return@withLock DeactivationResult.Inactive
        }
        mutableLifecycle.value = ConnectivityLifecycle.Deactivating
        val result = execute(listOf("adb", "reverse", "--remove", "tcp:$port"))
        mutableLifecycle.value = ConnectivityLifecycle.Inactive
        mutableHealth.value = if (result.exitCode == 0) ConnectivityHealth.Unknown
            else ConnectivityHealth.Degraded("adb_cleanup_failed")
        if (result.exitCode == 0) DeactivationResult.Inactive
        else DeactivationResult.Failed("adb_cleanup_failed")
    }

    private suspend fun execute(arguments: List<String>): ProcessCommandResult = try {
        commands.execute(arguments, COMMAND_TIMEOUT_MILLIS)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ProcessCommandResult(-1, "process_failed")
    }

    private companion object {
        private const val COMMAND_TIMEOUT_MILLIS: Long = 10_000L
    }
}
