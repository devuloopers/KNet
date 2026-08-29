package com.devuloopers.knet.connectivity.desktop.network

import kotlin.time.Clock
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkSnapshot
import com.devuloopers.knet.core.logger.KNetLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Raw platform scan result kept injectable for deterministic network-transition tests. */
public data class DesktopNetworkObservation(
    public val addresses: List<NetworkAddress>,
    public val defaultRouteAvailable: Boolean,
    public val vpnActive: Boolean,
    public val selection: DesktopLanAddressSelection? = null,
)

public fun interface DesktopNetworkScanner {
    public fun scan(): DesktopNetworkObservation
}

/**
 * Desktop network monitor that publishes metadata changes without controlling or restarting proxy
 * listeners. IPv4, IPv6, loopback, VPN, and interface identity remain explicit.
 */
public class DesktopNetworkSnapshotMonitor(
    private val scanner: DesktopNetworkScanner = JvmDesktopNetworkScanner(),
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
        logSelection(initial)
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
            observation.vpnActive == current.vpnActive &&
            observation.selection?.address == current.preferredLanAddress
        ) return current
        logSelection(observation)
        return observation.toSnapshot(version.incrementAndGet(), nowMillis()).also { mutableSnapshots.value = it }
    }

    override fun close() {
        pollingJob.cancel()
        scope.cancel()
    }
}

private fun DesktopNetworkObservation.toSnapshot(version: Long, now: Long): NetworkSnapshot = NetworkSnapshot(
    version = version,
    addresses = addresses,
    defaultRouteAvailable = defaultRouteAvailable,
    vpnActive = vpnActive,
    observedAtEpochMillis = now,
    preferredLanAddress = selection?.address,
)

private fun logSelection(observation: DesktopNetworkObservation) {
    KNetLogger.debug(tag = NETWORK_TAG) {
        "network_event=lan_candidates values=" + observation.addresses
            .filter { address -> !address.loopback }
            .joinToString(separator = ",") { address ->
                "${address.interfaceId}:${address.address}:${address.interfaceKind}:multicast=${address.multicastSupported}"
            }
    }
    val selection = observation.selection
    if (selection == null) {
        KNetLogger.warn(tag = NETWORK_TAG) {
            "network_event=lan_address_unavailable candidate_count=${observation.addresses.size}"
        }
        return
    }
    KNetLogger.info(tag = NETWORK_TAG) {
        "network_event=lan_address_selected address=${selection.address.address} " +
            "interface_id=${selection.address.interfaceId} " +
            "interface_name=${selection.address.interfaceDisplayName.sanitizeLogValue()} " +
            "interface_kind=${selection.address.interfaceKind} reason=${selection.reason}"
    }
}

private fun String.sanitizeLogValue(): String = replace(Regex("\\s+"), "_")

private const val NETWORK_TAG: String = "DesktopNetwork"
