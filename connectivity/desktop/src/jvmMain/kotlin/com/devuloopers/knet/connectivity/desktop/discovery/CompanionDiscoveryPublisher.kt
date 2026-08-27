package com.devuloopers.knet.connectivity.desktop.discovery

import com.devuloopers.knet.application.usecase.pairing.CompanionDiscoveryEnvironmentProvider
import com.devuloopers.knet.companion.model.CompanionDiscoveryAdvertisement
import com.devuloopers.knet.companion.model.CompanionDiscoveryProtocol
import com.devuloopers.knet.companion.model.CompanionDiscoveryTxtCodec
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.core.logger.KNetLogger
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One native DNS-SD registration owned by [CompanionDiscoveryPublisher]. */
public fun interface CompanionDiscoveryRegistration : AutoCloseable {
    public override fun close()
}

/** Injectable platform registrar used to keep network transition behavior deterministic in tests. */
public fun interface CompanionDiscoveryRegistrar {
    public fun register(
        address: String,
        instanceName: String,
        port: Int,
        txt: Map<String, String>,
    ): CompanionDiscoveryRegistration
}

/** JmDNS registration bound to the exact address published by the active Wi-Fi sharing session. */
public class JmDnsCompanionDiscoveryRegistrar : CompanionDiscoveryRegistrar {
    override fun register(
        address: String,
        instanceName: String,
        port: Int,
        txt: Map<String, String>,
    ): CompanionDiscoveryRegistration {
        val jmdns = JmDNS.create(InetAddress.getByName(address), instanceName)
        val service = ServiceInfo.create(
            CompanionDiscoveryProtocol.SERVICE_TYPE_FQDN,
            instanceName,
            port,
            0,
            0,
            txt,
        )
        try {
            jmdns.registerService(service)
        } catch (failure: Throwable) {
            runCatching(jmdns::close)
            throw failure
        }
        return CompanionDiscoveryRegistration {
            runCatching { jmdns.unregisterService(service) }
            runCatching(jmdns::close)
        }
    }
}

/**
 * Advertises only the currently reachable KNet control endpoint and replaces registration on network changes.
 *
 * TXT metadata remains an untrusted selection hint. Companion clients must authenticate the endpoint descriptor
 * with the already-pinned desktop TLS identity before updating any durable registration.
 */
public class CompanionDiscoveryPublisher(
    private val sharingState: StateFlow<WifiSharingState>,
    private val environmentProvider: CompanionDiscoveryEnvironmentProvider,
    private val registrar: CompanionDiscoveryRegistrar = JmDnsCompanionDiscoveryRegistrar(),
    private val txtCodec: CompanionDiscoveryTxtCodec = CompanionDiscoveryTxtCodec(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val registrationLock: Any = Any()
    private var activeRegistration: CompanionDiscoveryRegistration? = null
    private var publicationJob: Job? = null

    /** Starts observing Wi-Fi sharing once. */
    public fun start() {
        check(!closed.get()) { "Companion discovery publisher is closed." }
        if (publicationJob != null) return
        publicationJob = scope.launch {
            sharingState.collectLatest { state ->
                replaceRegistration(null)
                val active = state as? WifiSharingState.Active ?: return@collectLatest
                val environment = environmentProvider.load()
                val advertisement = CompanionDiscoveryAdvertisement(
                    protocolVersion = CompanionDiscoveryProtocol.VERSION,
                    desktopId = environment.desktopId,
                    legacyDesktopIds = environment.legacyDesktopIds,
                    runtimeId = environment.runtimeId,
                )
                val instanceName = "KNet-${environment.desktopId.value.take(8)}"
                runCatching {
                    registrar.register(
                        address = active.session.networkAddress.address,
                        instanceName = instanceName,
                        port = environment.controlPort,
                        txt = txtCodec.encode(advertisement),
                    )
                }.onSuccess { registration ->
                    replaceRegistration(registration)
                }.onFailure { failure ->
                    KNetLogger.warn(tag = "CompanionDiscovery") {
                        "Companion discovery advertisement is unavailable: ${failure::class.simpleName}."
                    }
                }
            }
        }
    }

    private fun replaceRegistration(registration: CompanionDiscoveryRegistration?) {
        val rejected = synchronized(registrationLock) {
            if (closed.get()) {
                true
            } else {
                activeRegistration?.let { active -> runCatching(active::close) }
                activeRegistration = registration
                false
            }
        }
        if (rejected) registration?.let { inactive -> runCatching(inactive::close) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        publicationJob?.cancel()
        publicationJob = null
        val registration = synchronized(registrationLock) {
            activeRegistration.also { activeRegistration = null }
        }
        registration?.let { active -> runCatching(active::close) }
        scope.cancel()
    }
}
