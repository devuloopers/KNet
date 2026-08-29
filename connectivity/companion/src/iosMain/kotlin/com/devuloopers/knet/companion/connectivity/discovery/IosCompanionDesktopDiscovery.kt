package com.devuloopers.knet.companion.connectivity.discovery

import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDiscoveryCandidate
import com.devuloopers.knet.companion.model.CompanionDiscoveryProtocol
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import com.devuloopers.knet.companion.model.CompanionDiscoveryTxtCodec
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import platform.posix.NI_NUMERICHOST
import platform.posix.getnameinfo
import platform.posix.memcpy

/**
 * iOS Bonjour adapter. Resolved services remain untrusted until common pinned-TLS reconciliation succeeds.
 *
 * The iOS product declares `_knet-companion._tcp` and its local-network usage text; this reusable adapter does not
 * own product Info.plist policy.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosCompanionDesktopDiscovery(
    private val txtCodec: CompanionDiscoveryTxtCodec = CompanionDiscoveryTxtCodec(),
) : CompanionDesktopDiscovery {
    private val mutableState = MutableStateFlow<CompanionDiscoveryState>(CompanionDiscoveryState.Idle)
    override val state: StateFlow<CompanionDiscoveryState> = mutableState.asStateFlow()
    private val services = linkedMapOf<String, NSNetService>()
    private val candidates = linkedMapOf<String, CompanionDiscoveryCandidate>()
    private var targets: Set<CompanionDesktopId> = emptySet()
    private var browser: NSNetServiceBrowser? = null
    private val delegate = IosBonjourDelegate(this)

    override fun start(targetDesktopIds: Set<CompanionDesktopId>) {
        require(targetDesktopIds.isNotEmpty()) { "At least one paired desktop identity is required." }
        stop()
        targets = targetDesktopIds.toSet()
        mutableState.value = CompanionDiscoveryState.Searching(targetDesktopIds.first())
        browser = NSNetServiceBrowser().also { active ->
            active.delegate = delegate
            active.includesPeerToPeer = true
            active.searchForServicesOfType(APPLE_SERVICE_TYPE, APPLE_LOCAL_DOMAIN)
        }
    }

    override fun stop() {
        browser?.apply {
            stop()
            delegate = null
        }
        browser = null
        services.values.forEach { service ->
            service.stop()
            service.delegate = null
        }
        services.clear()
        candidates.clear()
        targets = emptySet()
        mutableState.value = CompanionDiscoveryState.Idle
    }

    internal fun found(
        browser: NSNetServiceBrowser,
        service: NSNetService,
    ) {
        if (this.browser !== browser) return
        services[service.name] = service
        service.delegate = delegate
        service.resolveWithTimeout(RESOLUTION_TIMEOUT_SECONDS)
    }

    internal fun removed(
        browser: NSNetServiceBrowser,
        service: NSNetService,
    ) {
        if (this.browser !== browser) return
        services.remove(service.name)?.apply {
            stop()
            delegate = null
        }
        candidates.remove(service.name)
        publishCandidates()
    }

    internal fun searchFailed(browser: NSNetServiceBrowser) {
        if (this.browser !== browser) return
        fail("Local-network permission is required to rediscover KNet Desktop.")
    }

    internal fun serviceResolved(sender: NSNetService) {
        publishResolved(sender)
    }

    internal fun updated(sender: NSNetService, data: NSData) {
        publishResolved(sender, data)
    }

    internal fun resolveFailed(sender: NSNetService) {
        services.remove(sender.name)?.delegate = null
        candidates.remove(sender.name)
        publishCandidates()
    }

    private fun publishResolved(service: NSNetService, txtData: NSData? = service.TXTRecordData()) {
        if (services[service.name] !== service || targets.isEmpty()) return
        val advertisement = txtData
            ?.toTxtValues()
            ?.let { values -> runCatching { txtCodec.decode(values) }.getOrNull() }
            ?: return
        if (!advertisement.matches(targets)) return
        // A Packet Tunnel must exclude the paired desktop from its own routes. Publish numeric addresses instead of
        // the Bonjour host name so the extension can apply that exclusion without recursive DNS or route lookup.
        val endpoints = service.addresses.orEmpty()
            .filterIsInstance<NSData>()
            .mapNotNull { address -> address.numericHost() }
            .distinct()
            .mapNotNull { host ->
                runCatching {
                    CompanionServiceEndpoint(host, service.port.toInt(), scheme = CompanionEndpointScheme.HTTPS)
                }.getOrNull()
            }
        if (endpoints.isEmpty()) return
        candidates[service.name] = CompanionDiscoveryCandidate(
            instanceName = service.name,
            advertisement = advertisement,
            endpoints = endpoints,
        )
        service.startMonitoring()
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

    private fun fail(message: String) {
        mutableState.value = CompanionDiscoveryState.Failed(
            CompanionFailure(CompanionFailureCode.NETWORK_UNAVAILABLE, message, true),
        )
        browser?.apply {
            stop()
            delegate = null
        }
        browser = null
        services.values.forEach { service ->
            service.stop()
            service.delegate = null
        }
        services.clear()
        candidates.clear()
        targets = emptySet()
    }

    private fun NSData.toTxtValues(): Map<String, String>? = runCatching {
        NSNetService.dictionaryFromTXTRecordData(this).entries.associate { (rawKey, rawValue) ->
            val key = rawKey as? String ?: error("Invalid Bonjour TXT key.")
            val value = (rawValue as? NSData)?.toByteArray()?.decodeToString(throwOnInvalidSequence = true)
                ?: error("Invalid Bonjour TXT value.")
            key to value
        }
    }.getOrNull()

    private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { output ->
        if (output.isNotEmpty()) output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }

    private fun NSData.numericHost(): String? = memScoped {
        val socketAddress = bytes ?: return@memScoped null
        val output = allocArray<ByteVar>(NUMERIC_HOST_BUFFER_SIZE)
        val result = getnameinfo(
            socketAddress.reinterpret(),
            length.toUInt(),
            output,
            NUMERIC_HOST_BUFFER_SIZE.toUInt(),
            null,
            0u,
            NI_NUMERICHOST,
        )
        if (result == 0) output.toKString().takeIf(String::isNotBlank) else null
    }

}

private class IosBonjourDelegate(
    private val owner: IosCompanionDesktopDiscovery,
) : NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {
    @ObjCSignatureOverride
    override fun netServiceBrowser(
        browser: NSNetServiceBrowser,
        didFindService: NSNetService,
        moreComing: Boolean,
    ) {
        owner.found(browser, didFindService)
    }

    @ObjCSignatureOverride
    override fun netServiceBrowser(
        browser: NSNetServiceBrowser,
        didRemoveService: NSNetService,
        moreComing: Boolean,
    ) {
        owner.removed(browser, didRemoveService)
    }

    override fun netServiceBrowser(browser: NSNetServiceBrowser, didNotSearch: Map<Any?, *>) {
        owner.searchFailed(browser)
    }

    override fun netServiceDidResolveAddress(sender: NSNetService) {
        owner.serviceResolved(sender)
    }

    override fun netService(sender: NSNetService, didUpdateTXTRecordData: NSData) {
        owner.updated(sender, didUpdateTXTRecordData)
    }

    override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
        owner.resolveFailed(sender)
    }
}

private const val APPLE_SERVICE_TYPE: String = "${CompanionDiscoveryProtocol.SERVICE_TYPE}."
private const val APPLE_LOCAL_DOMAIN: String = "local."
private const val RESOLUTION_TIMEOUT_SECONDS: Double = 5.0
private const val NUMERIC_HOST_BUFFER_SIZE: Int = 1_025
