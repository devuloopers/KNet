package com.devuloopers.knet.application.usecase.pairing

import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopRuntimeId
import com.devuloopers.knet.companion.model.CompanionEndpointDescriptor
import com.devuloopers.knet.companion.model.CompanionDiscoveryProtocol

/** Process-owned canonical desktop identity used by pairing, discovery, and endpoint reconciliation. */
public data class CompanionDiscoveryEnvironment(
    public val desktopId: CompanionDesktopId,
    public val legacyDesktopIds: Set<CompanionDesktopId>,
    public val runtimeId: CompanionDesktopRuntimeId,
    public val controlPort: Int,
    public val proxyPort: Int,
) {
    init {
        require(desktopId !in legacyDesktopIds) { "Canonical desktop identity cannot also be a legacy alias." }
        require(legacyDesktopIds.size <= CompanionDiscoveryProtocol.MAXIMUM_LEGACY_IDS) {
            "Too many desktop discovery aliases."
        }
        require(controlPort in 1..65_535 && proxyPort in 1..65_535) { "Companion discovery ports are invalid." }
    }

    public fun endpointDescriptor(): CompanionEndpointDescriptor = CompanionEndpointDescriptor(
        protocolVersion = CompanionDiscoveryProtocol.VERSION,
        desktopId = desktopId,
        acceptedLegacyIds = legacyDesktopIds,
        runtimeId = runtimeId,
        controlPort = controlPort,
        proxyPort = proxyPort,
    )
}

/** Supplies one process-stable discovery identity without exposing its persistence implementation. */
public fun interface CompanionDiscoveryEnvironmentProvider {
    public fun load(): CompanionDiscoveryEnvironment
}
