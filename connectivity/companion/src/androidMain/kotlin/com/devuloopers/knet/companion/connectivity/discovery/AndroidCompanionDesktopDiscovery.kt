package com.devuloopers.knet.companion.connectivity.discovery

import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDiscoveryCandidate
import com.devuloopers.knet.companion.model.CompanionDiscoveryProtocol
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import com.devuloopers.knet.companion.model.CompanionDiscoveryTxtCodec
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import java.net.InetAddress
import kotlin.collections.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android DNS-SD adapter with continuous modern monitoring, serialized legacy resolution, and multicast support. */
internal class AndroidCompanionDesktopDiscovery(
    context: Context,
    private val txtCodec: CompanionDiscoveryTxtCodec = CompanionDiscoveryTxtCodec(),
) : CompanionDesktopDiscovery {
    private val applicationContext = context.applicationContext
    private val nsdManager = applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val lock = Any()
    private val mutableState = MutableStateFlow<CompanionDiscoveryState>(CompanionDiscoveryState.Idle)
    override val state: StateFlow<CompanionDiscoveryState> = mutableState.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var targets: Set<CompanionDesktopId> = emptySet()
    private var generation: Long = 0L
    private val candidates = linkedMapOf<String, CompanionDiscoveryCandidate>()
    private val pendingResolutions = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val continuousServiceMonitor: AndroidCompanionServiceMonitor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Android34CompanionServiceMonitor(
                nsdManager = nsdManager,
                callbackExecutor = applicationContext.mainExecutor,
                onServiceUpdated = ::acceptServiceUpdate,
                onServiceUnavailable = ::removeService,
            )
        } else {
            null
        }

    override fun start(targetDesktopIds: Set<CompanionDesktopId>) {
        require(targetDesktopIds.isNotEmpty()) { "At least one paired desktop identity is required." }
        stop()
        synchronized(lock) {
            generation += 1
            targets = targetDesktopIds.toSet()
            candidates.clear()
            pendingResolutions.clear()
            resolving = false
            mutableState.value = CompanionDiscoveryState.Searching(targetDesktopIds.first())
            multicastLock = wifiManager?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }
            val activeGeneration = generation
            val created = discoveryListener(activeGeneration)
            listener = created
            try {
                nsdManager.discoverServices(
                    CompanionDiscoveryProtocol.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    created,
                )
            } catch (_: SecurityException) {
                fail(discoveryUnavailable("Local-network permission is required to rediscover KNet Desktop."))
            } catch (_: RuntimeException) {
                fail(discoveryUnavailable("Unable to start local KNet desktop discovery."))
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            generation += 1
            listener?.let { active -> runCatching { nsdManager.stopServiceDiscovery(active) } }
            listener = null
            continuousServiceMonitor?.clear()
            pendingResolutions.clear()
            resolving = false
            candidates.clear()
            targets = emptySet()
            multicastLock?.let { active -> if (active.isHeld) active.release() }
            multicastLock = null
            mutableState.value = CompanionDiscoveryState.Idle
        }
    }

    private fun discoveryListener(activeGeneration: Long): NsdManager.DiscoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    if (generation != activeGeneration || !serviceInfo.serviceType.contains("_knet-companion._tcp")) return
                    continuousServiceMonitor?.observe(serviceInfo, activeGeneration) ?: run {
                        pendingResolutions.addLast(serviceInfo)
                        resolveNext(activeGeneration)
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    if (generation != activeGeneration) return
                    continuousServiceMonitor?.forget(serviceInfo)
                    candidates.remove(serviceInfo.serviceName)
                    publishCandidates()
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                synchronized(lock) {
                    if (generation == activeGeneration) fail(discoveryUnavailable("Unable to browse for KNet Desktop."))
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

    @Suppress("DEPRECATION")
    private fun resolveNext(activeGeneration: Long) {
        if (resolving || generation != activeGeneration) return
        val service = if (pendingResolutions.isEmpty()) null else pendingResolutions.removeFirst()
        service ?: return
        resolving = true
        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(lock) {
                        resolving = false
                        resolveNext(activeGeneration)
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) {
                        if (generation == activeGeneration) resolved(serviceInfo)
                        resolving = false
                        resolveNext(activeGeneration)
                    }
                }
            })
        } catch (_: RuntimeException) {
            resolving = false
            resolveNext(activeGeneration)
        }
    }

    private fun acceptServiceUpdate(activeGeneration: Long, serviceInfo: NsdServiceInfo) {
        synchronized(lock) {
            if (generation == activeGeneration) resolved(serviceInfo)
        }
    }

    private fun removeService(activeGeneration: Long, serviceName: String) {
        synchronized(lock) {
            if (generation != activeGeneration) return
            candidates.remove(serviceName)
            publishCandidates()
        }
    }

    private fun resolved(service: NsdServiceInfo) {
        val attributes = runCatching {
            service.attributes.mapValues { (_, bytes) -> bytes.decodeToString(throwOnInvalidSequence = true) }
        }.getOrNull() ?: return
        val advertisement = runCatching { txtCodec.decode(attributes) }.getOrNull() ?: return
        if (!advertisement.matches(targets)) return
        val addresses: List<InetAddress> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.hostAddresses
        } else {
            legacyHostAddresses(service)
        }
        val endpoints = addresses.mapNotNull { address ->
            val host = address.hostAddress?.substringBefore('%') ?: return@mapNotNull null
            runCatching {
                CompanionServiceEndpoint(
                    host = host,
                    port = service.port,
                    scheme = CompanionEndpointScheme.HTTPS,
                )
            }.getOrNull()
        }.distinct().take(CompanionDiscoveryProtocol.MAXIMUM_ADDRESSES)
        if (endpoints.isEmpty()) return
        candidates[service.serviceName] = CompanionDiscoveryCandidate(
            instanceName = service.serviceName,
            advertisement = advertisement,
            endpoints = endpoints,
        )
        publishCandidates()
    }

    private fun publishCandidates() {
        val desktopId = targets.firstOrNull() ?: return
        mutableState.value = if (candidates.isEmpty()) {
            CompanionDiscoveryState.Searching(desktopId)
        } else {
            CompanionDiscoveryState.Candidates(desktopId, candidates.values.toList())
        }
    }

    private fun fail(failure: CompanionFailure) {
        generation += 1
        mutableState.value = CompanionDiscoveryState.Failed(failure)
        listener?.let { active -> runCatching { nsdManager.stopServiceDiscovery(active) } }
        listener = null
        continuousServiceMonitor?.clear()
        pendingResolutions.clear()
        resolving = false
        candidates.clear()
        targets = emptySet()
        multicastLock?.let { active -> if (active.isHeld) active.release() }
        multicastLock = null
    }

    private fun discoveryUnavailable(message: String): CompanionFailure = CompanionFailure(
        CompanionFailureCode.PLATFORM_ADAPTER_UNAVAILABLE,
        message,
        true,
    )

    @Suppress("DEPRECATION")
    private fun legacyHostAddresses(serviceInfo: NsdServiceInfo): List<InetAddress> =
        listOfNotNull(serviceInfo.host)

    private companion object {
        const val MULTICAST_LOCK_TAG: String = "knet-companion-discovery"
    }
}
