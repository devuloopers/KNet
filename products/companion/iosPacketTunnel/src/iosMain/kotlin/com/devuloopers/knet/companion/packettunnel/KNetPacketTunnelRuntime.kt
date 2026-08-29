@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.packettunnel

import com.devuloopers.knet.companion.packettunnel.network.LocalSocks5Gateway
import com.devuloopers.knet.companion.packettunnel.options.IpAddressFamily
import com.devuloopers.knet.companion.packettunnel.options.TunnelException
import com.devuloopers.knet.companion.packettunnel.options.TunnelFailure
import com.devuloopers.knet.companion.packettunnel.options.TunnelStartOptions
import com.devuloopers.knet.companion.packettunnel.options.TunnelStartOptionsParser
import com.devuloopers.knet.companion.packettunnel.tunnel.HevTunnelEngine
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.NetworkExtension.NEDNSSettings
import platform.NetworkExtension.NEIPv4Route
import platform.NetworkExtension.NEIPv4Settings
import platform.NetworkExtension.NEIPv6Route
import platform.NetworkExtension.NEIPv6Settings
import platform.NetworkExtension.NEPacketTunnelNetworkSettings
import platform.NetworkExtension.NEPacketTunnelProvider

/**
 * Owns the iOS packet-tunnel runtime while Swift remains the required Network Extension entry point.
 */
public class KNetPacketTunnelRuntime {
    private var gateway: LocalSocks5Gateway? = null
    private var engine: HevTunnelEngine? = null

    public fun start(
        provider: NEPacketTunnelProvider,
        options: Map<Any?, *>?,
        completionHandler: (NSError?) -> Unit,
    ) {
        NSLog("companion_event=tunnel_start_requested")
        val startOptions = try {
            TunnelStartOptionsParser.parse(options)
        } catch (error: TunnelException) {
            fail("start_options", error, completionHandler)
            return
        }

        val newGateway = try {
            LocalSocks5Gateway(startOptions)
        } catch (error: TunnelException) {
            fail("local_gateway", error, completionHandler)
            return
        }
        gateway = newGateway
        newGateway.start { gatewayResult ->
            val socksPort = gatewayResult.getOrElse { error ->
                fail("local_gateway", error, completionHandler)
                return@start
            }
            applyNetworkSettings(provider, startOptions) { settingsError ->
                if (settingsError != null) {
                    fail(
                        stage = "network_settings",
                        error = TunnelFailure.UNABLE_TO_CONFIGURE_TUNNEL.exception(),
                        completionHandler = completionHandler,
                    )
                    return@applyNetworkSettings
                }
                try {
                    val newEngine = HevTunnelEngine()
                    engine = newEngine
                    newEngine.start(socksPort) { engineError ->
                        if (engineError != null) {
                            NSLog("companion_event=tunnel_failed stage=packet_engine")
                            provider.cancelTunnelWithError(engineError.asNSError())
                            reset()
                        }
                    }
                    NSLog("companion_event=tunnel_started")
                    completionHandler(null)
                } catch (error: TunnelException) {
                    fail("packet_engine", error, completionHandler)
                }
            }
        }
    }

    public fun stop() {
        NSLog("companion_event=tunnel_stopped")
        reset()
    }

    private fun applyNetworkSettings(
        provider: NEPacketTunnelProvider,
        options: TunnelStartOptions,
        completionHandler: (NSError?) -> Unit,
    ) {
        val settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress = options.proxyHost)
        settings.MTU = NSNumber(int = TUNNEL_MTU)

        val ipv4 = NEIPv4Settings(
            addresses = listOf(TUNNEL_IPV4_ADDRESS),
            subnetMasks = listOf(TUNNEL_IPV4_MASK),
        )
        ipv4.includedRoutes = listOf(NEIPv4Route.defaultRoute())
        val ipv4Exclusions = DNS_SERVERS.map { server ->
            NEIPv4Route(destinationAddress = server, subnetMask = HOST_IPV4_MASK)
        }.toMutableList()
        if (options.proxyAddressFamily == IpAddressFamily.IPV4) {
            ipv4Exclusions += NEIPv4Route(
                destinationAddress = options.proxyHost,
                subnetMask = HOST_IPV4_MASK,
            )
        }
        ipv4.excludedRoutes = ipv4Exclusions
        settings.IPv4Settings = ipv4

        val ipv6 = NEIPv6Settings(
            addresses = listOf(TUNNEL_IPV6_ADDRESS),
            networkPrefixLengths = listOf(NSNumber(int = TUNNEL_IPV6_PREFIX)),
        )
        ipv6.includedRoutes = listOf(NEIPv6Route.defaultRoute())
        if (options.proxyAddressFamily == IpAddressFamily.IPV6) {
            ipv6.excludedRoutes = listOf(
                NEIPv6Route(
                    destinationAddress = options.proxyHost,
                    networkPrefixLength = NSNumber(int = HOST_IPV6_PREFIX),
                ),
            )
        }
        settings.IPv6Settings = ipv6

        val dns = NEDNSSettings(servers = DNS_SERVERS)
        dns.matchDomains = listOf("")
        settings.DNSSettings = dns

        provider.setTunnelNetworkSettings(settings, completionHandler)
    }

    private fun fail(
        stage: String,
        error: Throwable,
        completionHandler: (NSError?) -> Unit,
    ) {
        NSLog("companion_event=tunnel_start_failed stage=$stage")
        reset()
        val nativeError = when (error) {
            is TunnelException -> error.asNSError()
            else -> TunnelFailure.UNABLE_TO_CONFIGURE_TUNNEL.exception(error).asNSError()
        }
        completionHandler(nativeError)
    }

    private fun reset() {
        engine?.stop()
        engine = null
        gateway?.stop()
        gateway = null
    }

    private companion object {
        const val TUNNEL_MTU: Int = 1_500
        const val TUNNEL_IPV4_ADDRESS: String = "198.18.0.1"
        const val TUNNEL_IPV4_MASK: String = "255.255.255.0"
        const val HOST_IPV4_MASK: String = "255.255.255.255"
        const val TUNNEL_IPV6_ADDRESS: String = "fd00::1"
        const val TUNNEL_IPV6_PREFIX: Int = 64
        const val HOST_IPV6_PREFIX: Int = 128
        val DNS_SERVERS: List<String> = listOf("1.1.1.1", "1.0.0.1")
    }
}
