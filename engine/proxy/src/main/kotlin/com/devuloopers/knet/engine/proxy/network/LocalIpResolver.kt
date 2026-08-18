package com.devuloopers.knet.engine.proxy.network

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Engine component for inspecting host network interfaces and discovering local IPv4 addresses.
 *
 * Provides both instant single-shot IP resolution and a reactive coroutine [Flow] stream
 * that emits updated IP addresses when host network interfaces change.
 */
class LocalIpResolver {

    /**
     * Resolves the primary active non-loopback IPv4 address for the host machine.
     *
     * Uses OS routing table inspection via UDP socket resolution first, followed by
     * site-local IPv4 network interface scanning fallback.
     *
     * @return Host IPv4 address string (e.g. "192.168.1.15"), or "127.0.0.1" fallback if offline.
     */
    fun getLocalIpAddress(): String {
        // 1. Query OS routing table for default route active local IP (UDP connect does not send network traffic)
        try {
            java.net.DatagramSocket().use { socket ->
                socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 10002)
                val hostAddress = socket.localAddress?.hostAddress
                if (!hostAddress.isNullOrBlank() && hostAddress != "0.0.0.0" && hostAddress != "127.0.0.1") {
                    return hostAddress
                }
            }
        } catch (_: Exception) {
            // Fallback to network interface scanning if OS routing table lookup fails
        }

        // 2. Fallback: Scan active network interfaces for site-local IPv4 addresses (192.168.x.x, 10.x.x.x)
        return try {
            val addresses = NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .toList()

            addresses.firstOrNull { it.isSiteLocalAddress }?.hostAddress
                ?: addresses.firstOrNull()?.hostAddress
                ?: "127.0.0.1"
        } catch (exception: Exception) {
            KNetLogger.error(LogTags.PROXY, exception) { "Failed to resolve host network interfaces: ${exception.message}" }
            "127.0.0.1"
        }
    }

    /**
     * Emits the active local LAN IPv4 address as a reactive Flow stream.
     * Automatically emits updated IP addresses when Wi-Fi or network interface changes occur.
     *
     * @param pollIntervalMs Ticker polling interval in milliseconds. Defaults to 3000ms.
     */
    fun observeLocalIpAddress(pollIntervalMs: Long = 3000L): Flow<String> = flow {
        while (currentCoroutineContext().isActive) {
            emit(getLocalIpAddress())
            delay(pollIntervalMs)
        }
    }.distinctUntilChanged()
}
