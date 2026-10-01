package com.devuloopers.knet.domain.protectedtraffic

/** Stable identifier for one user or built-in protected-traffic rule. */
@JvmInline
value class ProtectedTrafficRuleId(
    /** Persisted identifier value. */
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Protected-traffic rule ID must not be blank." }
    }
}

/** Stable identifier for a versioned built-in protected-service compatibility group. */
@JvmInline
value class ProtectedServiceGroupId(
    /** Persisted compatibility-group identifier. */
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Protected-service group ID must not be blank." }
    }
}

/** Stable application identity verified by a trusted ingress adapter. */
@JvmInline
value class ProtectedSourceApplicationId(
    /** Platform package or signing identifier. */
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Source application ID must not be blank." }
        require(value.length <= MAXIMUM_APPLICATION_ID_LENGTH) { "Source application ID is too long." }
    }

    private companion object {
        const val MAXIMUM_APPLICATION_ID_LENGTH: Int = 512
    }
}

/** User-selectable action for a matching protected transport flow. */
enum class ProtectedTrafficAction {
    /** Terminate supported TLS protocols using the KNet certificate authority. */
    INSPECT,

    /** Relay the connection unchanged while recording payload-opaque metadata. */
    TUNNEL,

    /** Reject the matching flow before it reaches the destination. */
    BLOCK,

    /** Route outside KNet when the active platform ingress can perform a real bypass. */
    BYPASS,
}

/** Transport selector used by protected-traffic rules. */
enum class ProtectedTransportProtocol {
    /** Ordered TCP byte stream. */
    TCP,

    /** UDP datagram flow. */
    UDP,
}

/** Address-family selector used by protected-traffic rules. */
enum class ProtectedIpFamily {
    /** IPv4 destination. */
    IPV4,

    /** IPv6 destination. */
    IPV6,
}

/** Origin of a rule, used to preserve explicit user overrides over compatibility defaults. */
enum class ProtectedTrafficRuleOrigin {
    /** Rule authored by the user or a user-visible context action. */
    USER,

    /** Versioned KNet compatibility rule controlled by a visible group toggle. */
    BUILT_IN,
}

/**
 * Normalized destination matcher that does not depend on platform networking APIs.
 *
 * Use [parse] for all external input so equivalent hosts and network prefixes share one canonical
 * representation and duplicate selectors can be rejected deterministically.
 */
sealed interface ProtectedDestinationSelector {
    /** Canonical persistence key including selector kind. */
    val canonicalKey: String

    /** Exact normalized DNS hostname or IP literal. */
    data class Exact(
        /** Canonical hostname or IP literal. */
        val value: String,
        /** Whether [value] is an IP literal rather than a DNS hostname. */
        val isIpLiteral: Boolean,
    ) : ProtectedDestinationSelector {
        init {
            require(value.isNotBlank() && value == value.lowercase() && !value.endsWith('.')) {
                "Exact destination must be canonical."
            }
            require(isIpLiteral == (parseIpAddress(value) != null)) {
                "Exact destination kind does not match its value."
            }
        }

        override val canonicalKey: String = "exact:$value"
    }

    /** DNS suffix matcher requiring at least one label before [suffix]. */
    data class WildcardDomain(
        /** Canonical suffix without the leading wildcard marker. */
        val suffix: String,
    ) : ProtectedDestinationSelector {
        init {
            require(suffix == normalizeDnsHost(suffix) && '.' in suffix) {
                "Wildcard destination suffix must be canonical."
            }
        }

        override val canonicalKey: String = "wildcard:$suffix"
    }

    /** Canonical IPv4 or IPv6 network prefix. */
    data class Cidr(
        /** Canonical network address with host bits cleared. */
        val networkAddress: String,
        /** Number of significant network bits. */
        val prefixLength: Int,
        /** Address family of [networkAddress]. */
        val family: ProtectedIpFamily,
    ) : ProtectedDestinationSelector {
        init {
            val parsed = parseIpAddress(networkAddress)
            require(parsed != null && parsed.family == family) { "CIDR network address is malformed." }
            require(prefixLength in 0..parsed.bitCount) { "CIDR prefix is outside its address family." }
            require(parsed.masked(prefixLength).canonical() == networkAddress) {
                "CIDR network address must have host bits cleared."
            }
        }

        override val canonicalKey: String = "cidr:$networkAddress/$prefixLength"
    }

    companion object {
        /**
         * Parses an exact hostname/IP, a `*.domain` wildcard, or an IPv4/IPv6 CIDR.
         *
         * @param rawSelector User or persistence input to normalize.
         * @return A canonical destination selector.
         * @throws IllegalArgumentException when the selector is blank or malformed.
         */
        fun parse(rawSelector: String): ProtectedDestinationSelector {
            val trimmed = rawSelector.trim()
            require(trimmed.isNotEmpty()) { "Protected destination must not be blank." }
            require(trimmed.length <= MAXIMUM_DESTINATION_LENGTH) { "Protected destination is too long." }
            if ('/' in trimmed) return parseCidr(trimmed)
            if (trimmed.startsWith("*.")) {
                val suffix = normalizeDnsHost(trimmed.removePrefix("*."))
                require('.' in suffix) { "Wildcard destination must contain a registrable suffix." }
                return WildcardDomain(suffix)
            }
            val address = parseIpAddress(trimmed)
            return if (address != null) {
                Exact(address.canonical(), isIpLiteral = true)
            } else {
                Exact(normalizeDnsHost(trimmed), isIpLiteral = false)
            }
        }

        private const val MAXIMUM_DESTINATION_LENGTH: Int = 512
    }
}

/**
 * One normalized rule applied only when all non-null selectors match a flow.
 *
 * @property id Stable persistence and decision-evidence identifier.
 * @property enabled Whether the rule participates in evaluation.
 * @property action Action selected when this rule wins.
 * @property sourceApplication Verified platform application identity, when scoped by application.
 * @property destination Optional exact, wildcard, or CIDR destination selector.
 * @property port Optional destination port.
 * @property transport Optional transport protocol.
 * @property ipFamily Optional destination address family.
 * @property origin User-authored or built-in origin.
 * @property groupId Required compatibility-group identity for built-in rules.
 * @property groupVersion Monotonic built-in definition version, or zero for user rules.
 */
data class ProtectedTrafficRule(
    val id: ProtectedTrafficRuleId,
    val enabled: Boolean = true,
    val action: ProtectedTrafficAction,
    val sourceApplication: ProtectedSourceApplicationId? = null,
    val destination: ProtectedDestinationSelector? = null,
    val port: Int? = null,
    val transport: ProtectedTransportProtocol? = null,
    val ipFamily: ProtectedIpFamily? = null,
    val origin: ProtectedTrafficRuleOrigin = ProtectedTrafficRuleOrigin.USER,
    val groupId: ProtectedServiceGroupId? = null,
    val groupVersion: Int = 0,
) {
    init {
        require(port == null || port in 1..65_535) { "Protected-traffic port must be between 1 and 65535." }
        require(
            sourceApplication != null || destination != null || port != null || transport != null || ipFamily != null,
        ) { "A protected-traffic rule must contain at least one selector." }
        require((origin == ProtectedTrafficRuleOrigin.BUILT_IN) == (groupId != null)) {
            "Only built-in rules may carry a compatibility-group ID."
        }
        require(groupVersion >= 0) { "Compatibility-group version must not be negative." }
        require(origin == ProtectedTrafficRuleOrigin.BUILT_IN || groupVersion == 0) {
            "User rules cannot carry a compatibility-group version."
        }
    }

    /** Canonical selector key used to reject effective duplicates independently of rule identity. */
    val selectorKey: String = listOf(
        sourceApplication?.value?.lowercase().orEmpty(),
        destination?.canonicalKey.orEmpty(),
        port?.toString().orEmpty(),
        transport?.name.orEmpty(),
        ipFamily?.name.orEmpty(),
        origin.name,
        groupId?.value.orEmpty(),
    ).joinToString("|")
}

/**
 * Complete immutable protected-traffic policy snapshot.
 *
 * @property defaultAction Fallback action when no enabled rule matches.
 * @property rules User and built-in rules retained in deterministic storage order.
 * @property enabledBuiltInGroups Built-in groups currently allowed to participate in evaluation.
 */
data class ProtectedTrafficConfiguration(
    val defaultAction: ProtectedTrafficAction = ProtectedTrafficAction.INSPECT,
    val rules: List<ProtectedTrafficRule> = emptyList(),
    val enabledBuiltInGroups: Set<ProtectedServiceGroupId> = emptySet(),
) {
    init {
        val duplicate = rules.groupBy(ProtectedTrafficRule::selectorKey).entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) { "Equivalent protected-traffic selectors are not allowed." }
        require(rules.map { it.id }.distinct().size == rules.size) { "Protected-traffic rule IDs must be unique." }
    }
}

/**
 * Trusted metadata available when evaluating one transport flow.
 *
 * @property sourceApplication Verified package or signing identity, when available.
 * @property destinationHost Normalized or raw destination hostname/SNI, when available.
 * @property destinationAddress Destination IPv4/IPv6 literal, when available.
 * @property port Destination port.
 * @property transport Flow transport protocol.
 */
data class ProtectedTrafficRequest(
    val sourceApplication: ProtectedSourceApplicationId? = null,
    val destinationHost: String? = null,
    val destinationAddress: String? = null,
    val port: Int,
    val transport: ProtectedTransportProtocol,
) {
    init {
        require(destinationHost != null || destinationAddress != null) {
            "A protected-traffic request requires a destination host or address."
        }
        require(port in 1..65_535) { "Protected-traffic request port must be between 1 and 65535." }
        require(destinationHost == null || destinationHost.isNotBlank()) { "Destination host must not be blank." }
        require(destinationAddress == null || parseIpAddress(destinationAddress) != null) {
            "Destination address must be an IPv4 or IPv6 literal."
        }
    }
}

/** Evidence identifying why a protected-traffic action was selected. */
sealed interface ProtectedTrafficDecisionEvidence {
    /** An enabled rule selected the action. */
    data class Rule(
        /** Winning stable rule identifier. */
        val ruleId: ProtectedTrafficRuleId,
        /** Compatibility group for a built-in winning rule. */
        val groupId: ProtectedServiceGroupId?,
    ) : ProtectedTrafficDecisionEvidence

    /** No rule matched, so the global default selected the action. */
    data object GlobalDefault : ProtectedTrafficDecisionEvidence
}

/**
 * Explainable result of evaluating one flow against an immutable snapshot.
 *
 * @property action Selected transport action.
 * @property evidence Exact rule or global-default evidence.
 */
data class ProtectedTrafficDecision(
    val action: ProtectedTrafficAction,
    val evidence: ProtectedTrafficDecisionEvidence,
)

private fun parseCidr(value: String): ProtectedDestinationSelector.Cidr {
    require(value.count { it == '/' } == 1) { "CIDR destination must contain one prefix separator." }
    val address = parseIpAddress(value.substringBefore('/'))
        ?: throw IllegalArgumentException("CIDR destination must contain an IP literal.")
    val prefixLength = value.substringAfter('/').toIntOrNull()
        ?: throw IllegalArgumentException("CIDR prefix must be numeric.")
    require(prefixLength in 0..address.bitCount) { "CIDR prefix is outside its address family." }
    val network = address.masked(prefixLength)
    return ProtectedDestinationSelector.Cidr(
        networkAddress = network.canonical(),
        prefixLength = prefixLength,
        family = network.family,
    )
}

private fun normalizeDnsHost(value: String): String {
    val normalized = value.trim().trimEnd('.').lowercase()
    require(normalized.isNotEmpty() && normalized.length <= 253) { "DNS destination is malformed." }
    require(normalized.none { it == ':' || it == '/' || it == '[' || it == ']' }) {
        "DNS destination contains an invalid delimiter."
    }
    val labels = normalized.split('.')
    require(labels.all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            label.first() != '-' &&
            label.last() != '-' &&
            label.all { character -> character.isLetterOrDigit() || character == '-' }
    }) { "DNS destination contains an invalid label." }
    return normalized
}

/** Internal platform-independent binary IP representation used for exact and prefix matching. */
internal data class ProtectedIpAddress(
    val bytes: ByteArray,
    val family: ProtectedIpFamily,
) {
    val bitCount: Int = bytes.size * 8

    fun masked(prefixLength: Int): ProtectedIpAddress {
        val masked = bytes.copyOf()
        for (index in masked.indices) {
            val remaining = prefixLength - (index * 8)
            masked[index] = when {
                remaining >= 8 -> masked[index]
                remaining <= 0 -> 0
                else -> ((masked[index].toInt() and 0xFF) and (0xFF shl (8 - remaining))).toByte()
            }
        }
        return copy(bytes = masked)
    }

    fun canonical(): String = when (family) {
        ProtectedIpFamily.IPV4 -> bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
        ProtectedIpFamily.IPV6 -> bytes.asList().chunked(2).joinToString(":") { pair ->
            (((pair[0].toInt() and 0xFF) shl 8) or (pair[1].toInt() and 0xFF)).toString(16)
        }
    }
}

/** Parses IPv4 and IPv6 literals without depending on JVM-only networking APIs. */
internal fun parseIpAddress(rawValue: String): ProtectedIpAddress? {
    val value = rawValue.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
    if (value.isEmpty()) return null
    if (':' !in value) return parseIpv4(value)
    return parseIpv6(value)
}

private fun parseIpv4(value: String): ProtectedIpAddress? {
    val segments = value.split('.')
    if (segments.size != 4) return null
    val bytes = ByteArray(4)
    segments.forEachIndexed { index, segment ->
        if (segment.isEmpty() || (segment.length > 1 && segment.startsWith('0'))) return null
        val octet = segment.toIntOrNull() ?: return null
        if (octet !in 0..255) return null
        bytes[index] = octet.toByte()
    }
    return ProtectedIpAddress(bytes, ProtectedIpFamily.IPV4)
}

private fun parseIpv6(value: String): ProtectedIpAddress? {
    if (value.count { it == ':' } < 2 || value.count { it == ':' } > 7 && "::" !in value) return null
    if (value.countDoubleColons() > 1) return null
    val halves = value.split("::", limit = 2)
    val left = parseIpv6Segments(halves.first(), allowIpv4Tail = false) ?: return null
    val right = if (halves.size == 2) {
        parseIpv6Segments(halves[1], allowIpv4Tail = true) ?: return null
    } else {
        emptyList()
    }
    val segmentCount = left.size + right.size
    val segments = when {
        halves.size == 1 && segmentCount == 8 -> left
        halves.size == 2 && segmentCount < 8 -> left + List(8 - segmentCount) { 0 } + right
        else -> return null
    }
    val bytes = ByteArray(16)
    segments.forEachIndexed { index, segment ->
        bytes[index * 2] = (segment ushr 8).toByte()
        bytes[index * 2 + 1] = segment.toByte()
    }
    return ProtectedIpAddress(bytes, ProtectedIpFamily.IPV6)
}

private fun parseIpv6Segments(value: String, allowIpv4Tail: Boolean): List<Int>? {
    if (value.isEmpty()) return emptyList()
    val tokens = value.split(':')
    val result = mutableListOf<Int>()
    tokens.forEachIndexed { index, token ->
        if (token.isEmpty()) return null
        if ('.' in token) {
            if (!allowIpv4Tail || index != tokens.lastIndex) return null
            val ipv4 = parseIpv4(token) ?: return null
            result += ((ipv4.bytes[0].toInt() and 0xFF) shl 8) or (ipv4.bytes[1].toInt() and 0xFF)
            result += ((ipv4.bytes[2].toInt() and 0xFF) shl 8) or (ipv4.bytes[3].toInt() and 0xFF)
        } else {
            if (token.length > 4 || token.any { it.digitToIntOrNull(16) == null }) return null
            result += token.toInt(16)
        }
    }
    return result
}

private fun String.countDoubleColons(): Int {
    var count = 0
    var offset = 0
    while (true) {
        val found = indexOf("::", offset)
        if (found < 0) return count
        count += 1
        offset = found + 2
    }
}
