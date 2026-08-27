package com.devuloopers.knet.companion.model

/** Authenticated request to resolve one saved canonical or legacy desktop identity. */
public data class CompanionEndpointReconciliationRequest(
    public val desktopId: CompanionDesktopId,
)

/** Authenticated canonical identity and ports returned after discovery candidate TLS verification. */
public data class CompanionEndpointDescriptor(
    public val protocolVersion: Int,
    public val desktopId: CompanionDesktopId,
    public val acceptedLegacyIds: Set<CompanionDesktopId>,
    public val runtimeId: CompanionDesktopRuntimeId,
    public val controlPort: Int,
    public val proxyPort: Int,
) {
    init {
        require(protocolVersion == CompanionDiscoveryProtocol.VERSION) { "Unsupported endpoint descriptor version." }
        require(desktopId !in acceptedLegacyIds) { "Canonical endpoint identity cannot also be an alias." }
        require(acceptedLegacyIds.size <= CompanionDiscoveryProtocol.MAXIMUM_LEGACY_IDS) {
            "Too many endpoint identity aliases."
        }
        require(controlPort in 1..65_535 && proxyPort in 1..65_535) { "Companion endpoint ports are invalid." }
    }

    public fun accepts(id: CompanionDesktopId): Boolean = id == desktopId || id in acceptedLegacyIds
}

/** Canonical bounded wire codec for discovery endpoint reconciliation. */
public class CompanionEndpointReconciliationCodec {
    public fun encodeRequest(request: CompanionEndpointReconciliationRequest): ByteArray =
        "desktopId=${request.desktopId.value}".encodeToByteArray().requireBounded()

    public fun decodeRequest(body: ByteArray): CompanionEndpointReconciliationRequest {
        val values = decode(body)
        require(values.keys == setOf("desktopId")) { "Endpoint reconciliation request fields are invalid." }
        return CompanionEndpointReconciliationRequest(CompanionDesktopId(values.getValue("desktopId")))
    }

    public fun encodeDescriptor(descriptor: CompanionEndpointDescriptor): ByteArray = buildString {
        append("version=").append(descriptor.protocolVersion).append('\n')
        append("desktopId=").append(descriptor.desktopId.value).append('\n')
        append("aliases=").append(descriptor.acceptedLegacyIds.sortedBy(CompanionDesktopId::value).joinToString(",") { it.value })
            .append('\n')
        append("runtimeId=").append(descriptor.runtimeId.value.toString()).append('\n')
        append("controlPort=").append(descriptor.controlPort).append('\n')
        append("proxyPort=").append(descriptor.proxyPort)
    }.encodeToByteArray().requireBounded()

    public fun decodeDescriptor(body: ByteArray): CompanionEndpointDescriptor {
        val values = decode(body)
        require(values.keys == DESCRIPTOR_KEYS) { "Endpoint descriptor fields are invalid." }
        val aliases = values.getValue("aliases").takeIf(String::isNotEmpty)?.split(',').orEmpty()
            .mapTo(linkedSetOf(), ::CompanionDesktopId)
        return CompanionEndpointDescriptor(
            protocolVersion = values.getValue("version").toInt(),
            desktopId = CompanionDesktopId(values.getValue("desktopId")),
            acceptedLegacyIds = aliases,
            runtimeId = CompanionDesktopRuntimeId.parse(values.getValue("runtimeId")),
            controlPort = values.getValue("controlPort").toInt(),
            proxyPort = values.getValue("proxyPort").toInt(),
        )
    }

    private fun decode(body: ByteArray): Map<String, String> {
        body.requireBounded()
        val values = linkedMapOf<String, String>()
        body.decodeToString(throwOnInvalidSequence = true).lineSequence().forEach { line ->
            val separator = line.indexOf('=')
            require(separator in 1 until line.length) { "Endpoint reconciliation field is invalid." }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            require(key.length <= 32 && value.length <= 512) { "Endpoint reconciliation value is invalid." }
            require(values.put(key, value) == null) { "Endpoint reconciliation field is duplicated." }
        }
        return values
    }

    private fun ByteArray.requireBounded(): ByteArray = apply {
        require(size in 1..CompanionControlProtocol.MAXIMUM_REQUEST_BYTES) {
            "Endpoint reconciliation payload size is invalid."
        }
    }

    private companion object {
        val DESCRIPTOR_KEYS: Set<String> = setOf(
            "version", "desktopId", "aliases", "runtimeId", "controlPort", "proxyPort",
        )
    }
}
