package com.devuloopers.knet.connectivity.desktop.network

import kotlin.time.Clock
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicLong

/** Raw platform scan result kept injectable for deterministic network-transition tests. */
public data class DesktopNetworkObservation(
    public val addresses: List<NetworkAddress>,
    public val defaultRouteAvailable: Boolean,
    public val vpnActive: Boolean,
)

public fun interface DesktopNetworkScanner {
    public fun scan(): DesktopNetworkObservation
}

/**
 * Desktop network monitor that publishes metadata changes without controlling or restarting proxy
 * listeners. IPv4, IPv6, loopback, VPN, and interface identity remain explicit.
 */
public class DesktopNetworkSnapshotMonitor(
    private val scanner: DesktopNetworkScanner = JvmNetworkScanner,
    private val pollIntervalMillis: Long = 2_000L,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val version = AtomicLong(0L)
    private val initial = scanner.scan()
    private val mutableSnapshots = MutableStateFlow(initial.toSnapshot(version.get(), nowMillis()))
    public val snapshots: StateFlow<NetworkSnapshot> = mutableSnapshots.asStateFlow()
    private val pollingJob: Job

    init {
        require(pollIntervalMillis >= 100L) { "Network polling interval must be at least 100 ms." }
        pollingJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMillis)
                refresh()
            }
        }
    }

    /** Performs one scan; version advances only when meaningful network state changes. */
    public fun refresh(): NetworkSnapshot {
        val observation = scanner.scan()
        val current = mutableSnapshots.value
        if (observation.addresses == current.addresses &&
            observation.defaultRouteAvailable == current.defaultRouteAvailable &&
            observation.vpnActive == current.vpnActive
        ) return current
        return observation.toSnapshot(version.incrementAndGet(), nowMillis()).also { mutableSnapshots.value = it }
    }

    override fun close() {
        pollingJob.cancel()
        scope.cancel()
    }
}

private object JvmNetworkScanner : DesktopNetworkScanner {
    override fun scan(): DesktopNetworkObservation {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { runCatching { it.isUp }.getOrDefault(false) }
        val addresses = interfaces.flatMap { networkInterface ->
            networkInterface.inetAddresses.toList().mapNotNull { address ->
                val family = when (address) {
                    is Inet4Address -> NetworkAddressFamily.IPV4
                    is Inet6Address -> NetworkAddressFamily.IPV6
                    else -> return@mapNotNull null
                }
                NetworkAddress(
                    interfaceId = networkInterface.name,
                    address = address.hostAddress.substringBefore('%'),
                    family = family,
                    loopback = address.isLoopbackAddress,
                )
            }
        }.sortedWith(compareBy(NetworkAddress::interfaceId, NetworkAddress::family, NetworkAddress::address))
        val activeNames = interfaces.map { it.name.lowercase() }
        return DesktopNetworkObservation(
            addresses = addresses,
            defaultRouteAvailable = addresses.any { !it.loopback },
            vpnActive = activeNames.any { name ->
                name.startsWith("utun") || name.startsWith("tun") || name.startsWith("tap") || name.contains("vpn")
            }
        )
    }
}

private fun DesktopNetworkObservation.toSnapshot(version: Long, now: Long): NetworkSnapshot = NetworkSnapshot(
    version = version,
    addresses = addresses,
    defaultRouteAvailable = defaultRouteAvailable,
    vpnActive = vpnActive,
    observedAtEpochMillis = now,
)
