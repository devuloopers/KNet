package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind

/** Authenticated source restriction applied by storage before paging, counts and history maintenance. */
public sealed interface TrafficSource {
    /** Explicit administrative scope retained for global maintenance and non-workspace consumers. */
    public data object All : TrafficSource

    /** Local, manual-proxy and historical traffic without a verified paired identity. */
    public data object Other : TrafficSource

    /** One cryptographic identity; display names, addresses and short suffixes are never matching keys. */
    public data class Device(
        /** Verified registered identity attached when the connection was admitted. */
        public val id: RegisteredDeviceId,
    ) : TrafficSource

    /** Stable non-secret preference key. It carries no credential or source address. */
    public val preferenceKey: String
        get() = when (this) {
            All -> "all"
            Other -> "other"
            is Device -> "device:${id.value}"
        }

    public companion object {
        /** Classifies only authenticated ingress categories; an IP-looking client identity is not a device. */
        public fun fromIngress(ingress: IngressContext?): TrafficSource {
            val verified = ingress?.kind == IngressKind.LanPairedDevice ||
                ingress?.kind == IngressKind.CompanionDirect || ingress?.kind == IngressKind.CompanionRelay
            val identity = ingress?.clientIdentity
            return if (verified && identity != null) {
                Device(RegisteredDeviceId(identity.value))
            } else Other
        }

        /** Restores a non-secret preference, falling back to Other for malformed or obsolete values. */
        public fun fromPreference(value: String?): TrafficSource = when {
            value == "all" -> All
            value?.startsWith("device:") == true && value.substring(7).isNotBlank() -> Device(RegisteredDeviceId(value.substring(7)))
            else -> Other
        }
    }
}
