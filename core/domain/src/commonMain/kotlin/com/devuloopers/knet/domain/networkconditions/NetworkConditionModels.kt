package com.devuloopers.knet.domain.networkconditions

/** Stable normalized identity for a user or built-in condition profile. */
@JvmInline
value class NetworkConditionProfileId(val value: String) {
    init {
        require(value.isNotBlank() && value == value.trim().lowercase()) {
            "Network condition profile ID must be a normalized lowercase token."
        }
    }
}

/** Stable rule identity independent from its target or selected profile. */
@JvmInline
value class NetworkConditionRuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "Network condition rule ID must not be blank." }
    }
}

/** One directional application/proxy-layer traffic budget. */
data class NetworkDirectionCondition(
    val bitsPerSecond: Long? = null,
    val utilizationPercent: Int = 100,
) {
    init {
        require(bitsPerSecond == null || bitsPerSecond in MINIMUM_BITS_PER_SECOND..MAXIMUM_BITS_PER_SECOND) {
            "Bandwidth must be unlimited or between $MINIMUM_BITS_PER_SECOND and $MAXIMUM_BITS_PER_SECOND bps."
        }
        require(utilizationPercent in 1..100) { "Bandwidth utilization must be between 1 and 100 percent." }
    }

    val effectiveBitsPerSecond: Long?
        get() = bitsPerSecond?.times(utilizationPercent)?.div(100L)?.coerceAtLeast(1L)

    companion object {
        const val MINIMUM_BITS_PER_SECOND: Long = 1_000L
        const val MAXIMUM_BITS_PER_SECOND: Long = 100_000_000_000L
    }
}

/** Explicit proxy-layer failure behaviour; no option claims to be IP packet loss. */
sealed interface NetworkFailureBehavior {
    data object None : NetworkFailureBehavior
    data object Offline : NetworkFailureBehavior
    data object Timeout : NetworkFailureBehavior
    data object ResetFlow : NetworkFailureBehavior

    data class SeededReset(
        val probabilityPercent: Int,
        val seed: Long,
    ) : NetworkFailureBehavior {
        init {
            require(probabilityPercent in 1..100) { "Fault probability must be between 1 and 100 percent." }
        }
    }
}

/** Immutable concrete values behind one Network Conditions profile. */
data class NetworkConditionProfile(
    val id: NetworkConditionProfileId,
    val name: String,
    val download: NetworkDirectionCondition = NetworkDirectionCondition(),
    val upload: NetworkDirectionCondition = NetworkDirectionCondition(),
    val latencyMillis: Long = 0L,
    val jitterMillis: Long = 0L,
    val virtualMtuBytes: Int = DEFAULT_VIRTUAL_MTU_BYTES,
    val failure: NetworkFailureBehavior = NetworkFailureBehavior.None,
    val packetLossPercent: Int = 0,
    val packetDuplicationPercent: Int = 0,
    val packetReorderingPercent: Int = 0,
    val builtIn: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Network condition profile name must not be blank." }
        require(latencyMillis in 0L..MAXIMUM_DELAY_MILLIS) { "Latency is outside the supported range." }
        require(jitterMillis in 0L..MAXIMUM_DELAY_MILLIS) { "Jitter is outside the supported range." }
        require(virtualMtuBytes in MINIMUM_VIRTUAL_MTU_BYTES..MAXIMUM_VIRTUAL_MTU_BYTES) {
            "Virtual MTU is outside the supported range."
        }
        require(packetLossPercent in 0..100) { "Packet loss must be between 0 and 100 percent." }
        require(packetDuplicationPercent in 0..100) { "Packet duplication must be between 0 and 100 percent." }
        require(packetReorderingPercent in 0..100) { "Packet reordering must be between 0 and 100 percent." }
    }

    val isPassThrough: Boolean
        get() = download.bitsPerSecond == null && upload.bitsPerSecond == null &&
            latencyMillis == 0L && jitterMillis == 0L &&
            virtualMtuBytes == DEFAULT_VIRTUAL_MTU_BYTES && failure == NetworkFailureBehavior.None

    val hasPacketEffects: Boolean
        get() = !isPassThrough || packetLossPercent > 0 || packetDuplicationPercent > 0 || packetReorderingPercent > 0

    companion object {
        const val DEFAULT_VIRTUAL_MTU_BYTES = 1_500
        const val MINIMUM_VIRTUAL_MTU_BYTES = 256
        const val MAXIMUM_VIRTUAL_MTU_BYTES = 65_535
        const val MAXIMUM_DELAY_MILLIS = 120_000L
    }
}

/** Validated exact host or wildcard subdomain target, optionally restricted to one port. */
@ConsistentCopyVisibility
data class NetworkConditionTarget private constructor(
    val normalizedHost: String,
    val wildcard: Boolean,
    val port: Int?,
) {
    init {
        require(port == null || port in 1..65_535) { "Network condition port is invalid." }
    }

    fun matches(host: String, destinationPort: Int): Boolean {
        val candidate = NetworkHostNormalizer.normalize(host) ?: return false
        if (port != null && port != destinationPort) return false
        return if (wildcard) {
            candidate.length > normalizedHost.length && candidate.endsWith(".$normalizedHost")
        } else {
            candidate == normalizedHost
        }
    }

    val displayValue: String
        get() = (if (wildcard) "*." else "") + normalizedHost + (port?.let { ":$it" } ?: "")

    companion object {
        fun parse(hostPattern: String, port: Int? = null): NetworkConditionTarget {
            val value = hostPattern.trim()
            val wildcard = value.startsWith("*.")
            val host = NetworkHostNormalizer.normalize(if (wildcard) value.drop(2) else value)
                ?: throw IllegalArgumentException("Network condition host is invalid.")
            require(!wildcard || !NetworkHostNormalizer.isIpLiteral(host)) {
                "Wildcard network conditions require a DNS suffix."
            }
            return NetworkConditionTarget(host, wildcard, port)
        }
    }
}

data class NetworkConditionRule(
    val id: NetworkConditionRuleId,
    val target: NetworkConditionTarget,
    val profileId: NetworkConditionProfileId,
    val enabled: Boolean = true,
)

data class NetworkConditionConfiguration(
    val enabled: Boolean = false,
    val globalProfileId: NetworkConditionProfileId? = null,
    val customProfiles: List<NetworkConditionProfile> = emptyList(),
    val rules: List<NetworkConditionRule> = emptyList(),
    val lastQuickAddProfileId: NetworkConditionProfileId? = null,
) {
    init {
        val allProfileIds = NetworkConditionBuiltIns.all.map { it.id }.toSet() + customProfiles.map { it.id }
        require(customProfiles.none(NetworkConditionProfile::builtIn)) { "Custom profiles cannot claim built-in ownership." }
        require(customProfiles.map { it.id }.distinct().size == customProfiles.size) { "Profile IDs must be unique." }
        require(rules.map { it.id }.distinct().size == rules.size) { "Rule IDs must be unique." }
        require(rules.map { it.target }.distinct().size == rules.size) { "Equal network condition targets are not allowed." }
        require(globalProfileId == null || globalProfileId in allProfileIds) { "Global profile is missing." }
        require(lastQuickAddProfileId == null || lastQuickAddProfileId in allProfileIds) {
            "Quick-add profile is missing."
        }
        require(rules.all { it.profileId in allProfileIds }) { "A network condition rule references a missing profile." }
    }

    val profiles: List<NetworkConditionProfile>
        get() = NetworkConditionBuiltIns.all + customProfiles

    fun profile(id: NetworkConditionProfileId?): NetworkConditionProfile? = profiles.firstOrNull { it.id == id }
}

data class EffectiveNetworkCondition(
    val profile: NetworkConditionProfile,
    val ruleId: NetworkConditionRuleId?,
    val source: Source,
) {
    enum class Source { DOMAIN_RULE, GLOBAL }
}

/** Pure deterministic precedence matcher shared by the proxy and packet adapters. */
object NetworkConditionMatcher {
    fun resolve(
        configuration: NetworkConditionConfiguration,
        host: String,
        port: Int,
    ): EffectiveNetworkCondition? {
        if (!configuration.enabled) return null
        val match = configuration.rules.asSequence()
            .filter(NetworkConditionRule::enabled)
            .filter { it.target.matches(host, port) }
            .sortedWith(
                compareByDescending<NetworkConditionRule> { !it.target.wildcard }
                    .thenByDescending { it.target.port != null }
                    .thenByDescending { it.target.normalizedHost.length },
            )
            .firstOrNull()
        if (match != null) {
            return EffectiveNetworkCondition(
                profile = checkNotNull(configuration.profile(match.profileId)),
                ruleId = match.id,
                source = EffectiveNetworkCondition.Source.DOMAIN_RULE,
            )
        }
        val global = configuration.profile(configuration.globalProfileId) ?: return null
        return EffectiveNetworkCondition(global, null, EffectiveNetworkCondition.Source.GLOBAL)
    }
}

/** Versioned code-owned preset catalog; rate values are bits per second. */
object NetworkConditionBuiltIns {
    val NO_THROTTLING = profile("no-throttling", "No throttling")
    val OFFLINE = profile("offline", "Offline", failure = NetworkFailureBehavior.Offline)
    val HIGH_LATENCY = profile("high-latency", "High latency", latency = 2_000)
    val SLOW_3G = profile("slow-3g", "Slow 3G", down = 400_000, up = 400_000, latency = 400)
    val FAST_3G = profile("fast-3g", "Fast 3G", down = 1_600_000, up = 750_000, latency = 150)
    val MOBILE_4G = profile("4g", "4G", down = 9_000_000, up = 4_000_000, latency = 50)
    val STREAMING_100_KBPS = profile(
        "streaming-100-kbps",
        "Streaming 100 kbps",
        down = 100_000,
        up = null,
        latency = 0,
    )
    val LOSSY_UNSTABLE = profile(
        "lossy-unstable",
        "Lossy / unstable",
        latency = 200,
        jitter = 100,
        failure = NetworkFailureBehavior.SeededReset(10, 0x4b4e4554L),
        packetLoss = 10,
        packetDuplication = 1,
        packetReordering = 5,
    )

    val all = listOf(
        NO_THROTTLING,
        OFFLINE,
        HIGH_LATENCY,
        SLOW_3G,
        FAST_3G,
        MOBILE_4G,
        STREAMING_100_KBPS,
        LOSSY_UNSTABLE,
    )

    private fun profile(
        id: String,
        name: String,
        down: Long? = null,
        up: Long? = null,
        latency: Long = 0,
        jitter: Long = 0,
        failure: NetworkFailureBehavior = NetworkFailureBehavior.None,
        packetLoss: Int = 0,
        packetDuplication: Int = 0,
        packetReordering: Int = 0,
    ) = NetworkConditionProfile(
        id = NetworkConditionProfileId(id),
        name = name,
        download = NetworkDirectionCondition(down),
        upload = NetworkDirectionCondition(up),
        latencyMillis = latency,
        jitterMillis = jitter,
        failure = failure,
        packetLossPercent = packetLoss,
        packetDuplicationPercent = packetDuplication,
        packetReorderingPercent = packetReordering,
        builtIn = true,
    )
}

/** Platform normalization keeps IDNA and numeric address handling out of policy code. */
expect object NetworkHostNormalizer {
    fun normalize(value: String): String?
    fun isIpLiteral(normalizedValue: String): Boolean
}
