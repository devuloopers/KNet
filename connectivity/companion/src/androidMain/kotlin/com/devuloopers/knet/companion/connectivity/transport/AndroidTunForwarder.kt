package com.devuloopers.knet.companion.connectivity.transport

import android.content.Context
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.amnezia.awg.hevtunnel.TProxyService

/** Result of acquiring the Android TUN-to-SOCKS forwarding resources. */
public sealed interface AndroidTunForwarderStartResult {
    /** Packet translation and the authenticated local carrier are running. */
    public data object Started : AndroidTunForwarderStartResult

    /** Startup failed before a usable forwarding path existed. */
    public data object Failed : AndroidTunForwarderStartResult
}

/** Replaceable Android TUN forwarding boundary owned by the product's VPN service. */
public interface AndroidTunForwarder {
    /** Starts one forwarding session over an already-established [tunFileDescriptor]. */
    public suspend fun start(
        tunFileDescriptor: Int,
        configuration: CompanionInspectionConfiguration,
        protector: AndroidSocketProtector,
    ): AndroidTunForwarderStartResult

    /** Stops translation and closes every active local carrier flow. Repeated calls are safe. */
    public suspend fun stop()
}

/**
 * Production Android TUN data-plane owner.
 *
 * The native translator sees only a loopback SOCKS endpoint. That endpoint applies KNet authentication and pins the
 * paired desktop identity; the native component never receives pairing credentials or remote desktop coordinates.
 */
public class PlatformAndroidTunForwarder internal constructor(
    private val configurationDirectory: File,
    private val transport: AndroidCompanionProxyTransport,
    private val engine: AndroidTun2SocksEngine,
) : AndroidTunForwarder {
    /** Creates the production Android forwarding adapter. */
    public constructor(
        context: Context,
        transport: AndroidCompanionProxyTransport,
    ) : this(
        configurationDirectory = File(context.applicationContext.cacheDir, CONFIGURATION_DIRECTORY),
        transport = transport,
        engine = HevAndroidTun2SocksEngine,
    )

    private val lifecycleLock: Mutex = Mutex()
    private var active: ActiveForwarding? = null

    /** Starts one forwarding session over an already-established [tunFileDescriptor]. */
    override suspend fun start(
        tunFileDescriptor: Int,
        configuration: CompanionInspectionConfiguration,
        protector: AndroidSocketProtector,
    ): AndroidTunForwarderStartResult = lifecycleLock.withLock {
        if (active != null) return@withLock AndroidTunForwarderStartResult.Started
        if (tunFileDescriptor < 0 || configuration.mode != CompanionInspectionMode.DEVICE_VPN) {
            return@withLock AndroidTunForwarderStartResult.Failed
        }
        withContext(Dispatchers.IO) {
            val socks = LocalSocks5Gateway(
                transport = transport,
                protector = protector,
                unsupportedTrafficPolicy = configuration.unsupportedTrafficPolicy,
            )
            var configFile: File? = null
            try {
                socks.start()
                configFile = writeConfiguration(socks.port)
                val engineFailure = AtomicReference<Throwable?>(null)
                val engineThread = Thread(
                    {
                        try {
                            engine.start(configFile.absolutePath, tunFileDescriptor)
                        } catch (failure: Throwable) {
                            engineFailure.set(failure)
                        }
                    },
                    "knet-companion-tun2socks",
                ).apply {
                    isDaemon = true
                    start()
                }
                repeat(ENGINE_START_CHECKS) {
                    delay(ENGINE_START_CHECK_INTERVAL_MILLIS)
                    if (engineFailure.get() != null) throw EngineStartException()
                }
                active = ActiveForwarding(socks, configFile, engineThread)
                AndroidTunForwarderStartResult.Started
            } catch (cancelled: CancellationException) {
                runCatching(engine::stop)
                socks.close()
                configFile?.delete()
                throw cancelled
            } catch (_: Exception) {
                runCatching(engine::stop)
                socks.close()
                configFile?.delete()
                AndroidTunForwarderStartResult.Failed
            } catch (_: LinkageError) {
                runCatching(engine::stop)
                socks.close()
                configFile?.delete()
                AndroidTunForwarderStartResult.Failed
            }
        }
    }

    /** Stops translation and closes every active local carrier flow. Repeated calls are safe. */
    override suspend fun stop(): Unit = lifecycleLock.withLock {
        val current = active ?: return@withLock
        active = null
        withContext(Dispatchers.IO) {
            runCatching(engine::stop)
            current.socks.close()
            runCatching { current.engineThread.join(ENGINE_STOP_JOIN_MILLIS) }
            current.configFile.delete()
        }
    }

    private fun writeConfiguration(socksPort: Int): File {
        check(configurationDirectory.mkdirs() || configurationDirectory.isDirectory) {
            "Unable to create the inspection configuration directory."
        }
        return File(configurationDirectory, CONFIGURATION_FILE).apply {
            writeText(renderTun2SocksConfiguration(socksPort))
        }
    }

    private data class ActiveForwarding(
        val socks: LocalSocks5Gateway,
        val configFile: File,
        val engineThread: Thread,
    )

    private companion object {
        private const val CONFIGURATION_DIRECTORY: String = "inspection"
        private const val CONFIGURATION_FILE: String = "tun2socks.yml"
        private const val ENGINE_START_CHECKS: Int = 4
        private const val ENGINE_START_CHECK_INTERVAL_MILLIS: Long = 25L
        private const val ENGINE_STOP_JOIN_MILLIS: Long = 2_000L
    }
}

internal fun renderTun2SocksConfiguration(socksPort: Int): String {
    require(socksPort in 1..65_535) { "SOCKS port must be valid." }
    return """
        misc:
          task-stack-size: 20480
          log-level: warn
        tunnel:
          mtu: 1500
          icmp: 'reply'
          multi-queue: false
        socks5:
          port: $socksPort
          address: '127.0.0.1'
          udp: 'udp'
    """.trimIndent()
}

private class EngineStartException : Exception()

internal interface AndroidTun2SocksEngine {
    fun start(configurationPath: String, tunFileDescriptor: Int)
    fun stop()
}

private object HevAndroidTun2SocksEngine : AndroidTun2SocksEngine {
    override fun start(configurationPath: String, tunFileDescriptor: Int) {
        TProxyService.TProxyStartService(configurationPath, tunFileDescriptor)
    }

    override fun stop() {
        TProxyService.TProxyStopService()
    }
}
