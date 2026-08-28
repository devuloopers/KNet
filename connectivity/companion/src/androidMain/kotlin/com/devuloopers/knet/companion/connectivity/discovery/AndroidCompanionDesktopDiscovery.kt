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
import com.devuloopers.knet.core.logger.KNetLogger
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
            KNetLogger.info(DISCOVERY_TAG) {
                "companion_event=discovery_start platform=android api=${Build.VERSION.SDK_INT} " +
                    "service_type=${CompanionDiscoveryProtocol.SERVICE_TYPE} " +
                    "targets=${targetDesktopIds.joinToString(",", transform = CompanionDesktopId::value)} " +
                    "multicast_lock=${multicastLock?.isHeld == true}"
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
            } catch (failure: SecurityException) {
                KNetLogger.error(DISCOVERY_TAG, failure) {
                    "companion_event=discovery_start_failed reason=permission"
                }
                fail(discoveryUnavailable("Local-network permission is required to rediscover KNet Desktop."))
            } catch (failure: RuntimeException) {
                KNetLogger.error(DISCOVERY_TAG, failure) {
                    "companion_event=discovery_start_failed reason=${failure::class.simpleName ?: "runtime"}"
                }
                fail(discoveryUnavailable("Unable to start local KNet desktop discovery."))
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            val wasActive = listener != null || targets.isNotEmpty()
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
            if (wasActive) {
                KNetLogger.debug(DISCOVERY_TAG) { "companion_event=discovery_stopped platform=android" }
            }
        }
    }

    private fun discoveryListener(activeGeneration: Long): NsdManager.DiscoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                KNetLogger.info(DISCOVERY_TAG) {
                    "companion_event=discovery_browse_started service_type=$serviceType"
                }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    if (generation != activeGeneration) return
                    KNetLogger.info(DISCOVERY_TAG) {
                        "companion_event=discovery_service_found name=${serviceInfo.serviceName} " +
                            "service_type=${serviceInfo.serviceType} port=${serviceInfo.port}"
                    }
                    if (!serviceInfo.serviceType.contains(CompanionDiscoveryProtocol.SERVICE_TYPE)) {
                        KNetLogger.debug(DISCOVERY_TAG) {
                            "companion_event=discovery_service_ignored name=${serviceInfo.serviceName} reason=service_type"
                        }
                        return
                    }
                    continuousServiceMonitor?.observe(serviceInfo, activeGeneration) ?: run {
                        pendingResolutions.addLast(serviceInfo)
                        KNetLogger.debug(DISCOVERY_TAG) {
                            "companion_event=discovery_resolution_queued name=${serviceInfo.serviceName} " +
                                "queue_size=${pendingResolutions.size} mode=legacy"
                        }
                        resolveNext(activeGeneration)
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    if (generation != activeGeneration) return
                    continuousServiceMonitor?.forget(serviceInfo)
                    candidates.remove(serviceInfo.serviceName)
                    KNetLogger.info(DISCOVERY_TAG) {
                        "companion_event=discovery_service_lost name=${serviceInfo.serviceName}"
                    }
                    publishCandidates()
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                KNetLogger.debug(DISCOVERY_TAG) {
                    "companion_event=discovery_browse_stopped service_type=$serviceType"
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                synchronized(lock) {
                    if (generation == activeGeneration) {
                        KNetLogger.error(DISCOVERY_TAG) {
                            "companion_event=discovery_browse_failed operation=start " +
                                "service_type=$serviceType error_code=$errorCode"
                        }
                        fail(discoveryUnavailable("Unable to browse for KNet Desktop."))
                    }
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                KNetLogger.warn(DISCOVERY_TAG) {
                    "companion_event=discovery_browse_failed operation=stop " +
                        "service_type=$serviceType error_code=$errorCode"
                }
            }
        }

    @Suppress("DEPRECATION")
    private fun resolveNext(activeGeneration: Long) {
        if (resolving || generation != activeGeneration) return
        val service = if (pendingResolutions.isEmpty()) null else pendingResolutions.removeFirst()
        service ?: return
        resolving = true
        KNetLogger.debug(DISCOVERY_TAG) {
            "companion_event=discovery_resolution_started name=${service.serviceName} mode=legacy"
        }
        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(lock) {
                        KNetLogger.warn(DISCOVERY_TAG) {
                            "companion_event=discovery_resolution_failed name=${serviceInfo.serviceName} " +
                                "mode=legacy error_code=$errorCode"
                        }
                        resolving = false
                        resolveNext(activeGeneration)
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) {
                        KNetLogger.debug(DISCOVERY_TAG) {
                            "companion_event=discovery_resolution_completed name=${serviceInfo.serviceName} mode=legacy"
                        }
                        if (generation == activeGeneration) resolved(serviceInfo)
                        resolving = false
                        resolveNext(activeGeneration)
                    }
                }
            })
        } catch (failure: RuntimeException) {
            KNetLogger.error(DISCOVERY_TAG, failure) {
                "companion_event=discovery_resolution_failed name=${service.serviceName} " +
                    "mode=legacy reason=${failure::class.simpleName ?: "runtime"}"
            }
            resolving = false
            resolveNext(activeGeneration)
        }
    }

    private fun acceptServiceUpdate(activeGeneration: Long, serviceInfo: NsdServiceInfo) {
        synchronized(lock) {
            if (generation == activeGeneration) {
                KNetLogger.debug(DISCOVERY_TAG) {
                    "companion_event=discovery_resolution_completed name=${serviceInfo.serviceName} mode=continuous"
                }
                resolved(serviceInfo)
            }
        }
    }

    private fun removeService(activeGeneration: Long, serviceName: String) {
        synchronized(lock) {
            if (generation != activeGeneration) return
            candidates.remove(serviceName)
            KNetLogger.info(DISCOVERY_TAG) {
                "companion_event=discovery_service_unavailable name=$serviceName mode=continuous"
            }
            publishCandidates()
        }
    }

    private fun resolved(service: NsdServiceInfo) {
        val attributesResult = runCatching {
            service.attributes.mapValues { (_, bytes) -> bytes.decodeToString(throwOnInvalidSequence = true) }
        }
        val attributes = attributesResult.getOrElse { failure ->
            KNetLogger.warn(DISCOVERY_TAG) {
                "companion_event=discovery_candidate_rejected name=${service.serviceName} " +
                    "reason=txt_decode failure=${failure::class.simpleName ?: "unknown"}"
            }
            return
        }
        val advertisementResult = runCatching { txtCodec.decode(attributes) }
        val advertisement = advertisementResult.getOrElse { failure ->
            KNetLogger.warn(DISCOVERY_TAG) {
                "companion_event=discovery_candidate_rejected name=${service.serviceName} " +
                    "reason=txt_contract keys=${attributes.keys.sorted().joinToString(",")} " +
                    "failure=${failure::class.simpleName ?: "unknown"}"
            }
            return
        }
        if (!advertisement.matches(targets)) {
            KNetLogger.info(DISCOVERY_TAG) {
                "companion_event=discovery_candidate_rejected name=${service.serviceName} reason=desktop_id " +
                    "advertised_id=${advertisement.desktopId.value} " +
                    "targets=${targets.joinToString(",", transform = CompanionDesktopId::value)}"
            }
            return
        }
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
        if (endpoints.isEmpty()) {
            KNetLogger.warn(DISCOVERY_TAG) {
                "companion_event=discovery_candidate_rejected name=${service.serviceName} reason=no_address " +
                    "resolved_address_count=${addresses.size} port=${service.port}"
            }
            return
        }
        candidates[service.serviceName] = CompanionDiscoveryCandidate(
            instanceName = service.serviceName,
            advertisement = advertisement,
            endpoints = endpoints,
        )
        KNetLogger.info(DISCOVERY_TAG) {
            "companion_event=discovery_candidate_accepted name=${service.serviceName} " +
                "desktop_id=${advertisement.desktopId.value} runtime_id=${advertisement.runtimeId.value} " +
                "endpoints=${endpoints.joinToString(",") { endpoint -> "${endpoint.host}:${endpoint.port}" }}"
        }
        publishCandidates()
    }

    private fun publishCandidates() {
        val desktopId = targets.firstOrNull() ?: return
        mutableState.value = if (candidates.isEmpty()) {
            CompanionDiscoveryState.Searching(desktopId)
        } else {
            CompanionDiscoveryState.Candidates(desktopId, candidates.values.toList())
        }
        KNetLogger.debug(DISCOVERY_TAG) {
            "companion_event=discovery_state target=${desktopId.value} candidate_count=${candidates.size}"
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
        KNetLogger.warn(DISCOVERY_TAG) {
            "companion_event=discovery_failed code=${failure.code} recoverable=${failure.recoverable}"
        }
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
        const val DISCOVERY_TAG: String = "CompanionDiscovery"
    }
}
