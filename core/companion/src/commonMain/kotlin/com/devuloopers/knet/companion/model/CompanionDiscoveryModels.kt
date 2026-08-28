package com.devuloopers.knet.companion.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Canonical UUID identifying one running KNet desktop process across all advertised interfaces. */
@JvmInline
public value class CompanionDesktopRuntimeId(public val value: Uuid) {
    public companion object {
        /** Parses an untrusted canonical UUID representation into a strongly typed runtime identity. */
        public fun parse(value: String): CompanionDesktopRuntimeId =
            CompanionDesktopRuntimeId(Uuid.parse(value))
    }
}

/** Non-secret DNS-SD metadata used only to select candidates for authenticated reconciliation. */
public data class CompanionDiscoveryAdvertisement(
    public val protocolVersion: Int,
    public val desktopId: CompanionDesktopId,
    public val legacyDesktopIds: Set<CompanionDesktopId>,
    public val runtimeId: CompanionDesktopRuntimeId,
) {
    init {
        require(protocolVersion == CompanionDiscoveryProtocol.VERSION) { "Unsupported companion discovery version." }
        require(desktopId !in legacyDesktopIds) { "Canonical desktop ID cannot also be a discovery alias." }
        require(legacyDesktopIds.size <= CompanionDiscoveryProtocol.MAXIMUM_LEGACY_IDS) {
            "Too many companion discovery aliases."
        }
    }

    /** Returns true only when this advertisement claims one explicitly paired identity. */
    public fun matches(targets: Set<CompanionDesktopId>): Boolean =
        desktopId in targets || legacyDesktopIds.any(targets::contains)
}

/** One resolved but still untrusted DNS-SD candidate. */
public data class CompanionDiscoveryCandidate(
    public val instanceName: String,
    public val advertisement: CompanionDiscoveryAdvertisement,
    public val endpoints: List<CompanionServiceEndpoint>,
) {
    init {
        require(instanceName.length in 1..128 && instanceName.none(Char::isISOControl)) {
            "Companion discovery instance name is invalid."
        }
        require(endpoints.isNotEmpty() && endpoints.size <= CompanionDiscoveryProtocol.MAXIMUM_ADDRESSES) {
            "Companion discovery candidate must contain a bounded endpoint set."
        }
        require(endpoints.all { it.scheme == CompanionEndpointScheme.HTTPS }) {
            "Companion discovery endpoints must be secure."
        }
    }
}

/** Portable discovery lifecycle exposed to common application and presentation layers. */
public sealed interface CompanionDiscoveryState {
    public data object Idle : CompanionDiscoveryState
    public data class Searching(public val desktopId: CompanionDesktopId) : CompanionDiscoveryState
    public data class Candidates(
        public val desktopId: CompanionDesktopId,
        public val values: List<CompanionDiscoveryCandidate>,
    ) : CompanionDiscoveryState
    public data class Failed(public val failure: CompanionFailure) : CompanionDiscoveryState
}

/** DNS-SD service and TXT record contract shared by desktop, Android, and iOS. */
public object CompanionDiscoveryProtocol {
    public const val VERSION: Int = 1
    public const val SERVICE_TYPE: String = "_knet-companion._tcp"
    public const val SERVICE_TYPE_FQDN: String = "_knet-companion._tcp.local."
    public const val TXT_VERSION: String = "v"
    public const val TXT_DESKTOP_ID: String = "id"
    public const val TXT_LEGACY_IDS: String = "aliases"
    public const val TXT_RUNTIME_ID: String = "runtime"
    public const val MAXIMUM_LEGACY_IDS: Int = 4
    public const val MAXIMUM_ADDRESSES: Int = 16
    public const val MAXIMUM_TXT_BYTES: Int = 512
}

/** Strict codec for the untrusted TXT metadata emitted by DNS-SD. */
public class CompanionDiscoveryTxtCodec {
    public fun encode(advertisement: CompanionDiscoveryAdvertisement): Map<String, String> = linkedMapOf(
        CompanionDiscoveryProtocol.TXT_VERSION to advertisement.protocolVersion.toString(),
        CompanionDiscoveryProtocol.TXT_DESKTOP_ID to advertisement.desktopId.value,
        CompanionDiscoveryProtocol.TXT_LEGACY_IDS to advertisement.legacyDesktopIds
            .sortedBy(CompanionDesktopId::value)
            .joinToString(",", transform = CompanionDesktopId::value),
        CompanionDiscoveryProtocol.TXT_RUNTIME_ID to advertisement.runtimeId.value.toString(),
    ).also(::requireBounded)

    public fun decode(values: Map<String, String>): CompanionDiscoveryAdvertisement {
        require(values.keys == EXPECTED_KEYS) { "Companion discovery TXT keys are invalid." }
        requireBounded(values)
        val aliases = values.getValue(CompanionDiscoveryProtocol.TXT_LEGACY_IDS)
            .takeIf(String::isNotEmpty)
            ?.split(',')
            .orEmpty()
            .mapTo(linkedSetOf(), ::CompanionDesktopId)
        return CompanionDiscoveryAdvertisement(
            protocolVersion = values.getValue(CompanionDiscoveryProtocol.TXT_VERSION).toInt(),
            desktopId = CompanionDesktopId(values.getValue(CompanionDiscoveryProtocol.TXT_DESKTOP_ID)),
            legacyDesktopIds = aliases,
            runtimeId = CompanionDesktopRuntimeId.parse(values.getValue(CompanionDiscoveryProtocol.TXT_RUNTIME_ID)),
        )
    }

    private fun requireBounded(values: Map<String, String>) {
        val encodedBytes = values.entries.sumOf { (key, value) -> key.encodeToByteArray().size + value.encodeToByteArray().size + 2 }
        require(encodedBytes <= CompanionDiscoveryProtocol.MAXIMUM_TXT_BYTES) {
            "Companion discovery TXT payload is too large."
        }
    }

    private companion object {
        val EXPECTED_KEYS: Set<String> = setOf(
            CompanionDiscoveryProtocol.TXT_VERSION,
            CompanionDiscoveryProtocol.TXT_DESKTOP_ID,
            CompanionDiscoveryProtocol.TXT_LEGACY_IDS,
            CompanionDiscoveryProtocol.TXT_RUNTIME_ID,
        )
    }
}
