package com.devuloopers.knet.traffic.model

/** Stable origin of an effective Network Conditions selection. */
public enum class AppliedNetworkConditionSource {
    /** A destination rule selected the profile. */
    DOMAIN_RULE,

    /** A bounded protocol-aware rule selected the profile for one request, stream, or message. */
    PROTOCOL_RULE,

    /** The enabled global condition selected the profile. */
    GLOBAL,
}

/**
 * Payload-free evidence of the Network Conditions policy selected for one exchange or flow.
 *
 * @property profileId Stable profile identity used when the traffic was admitted.
 * @property ruleId Stable destination-rule identity, or `null` for a global selection.
 * @property source Whether a destination rule, protocol-aware rule, or global fallback selected the profile.
 */
public data class AppliedNetworkCondition(
    public val profileId: String,
    public val ruleId: String?,
    public val source: AppliedNetworkConditionSource,
) {
    init {
        require(profileId.isNotBlank()) { "Applied condition profile ID must not be blank." }
        require(ruleId == null || ruleId.isNotBlank()) { "Applied condition rule ID must not be blank." }
        require((source == AppliedNetworkConditionSource.GLOBAL) == (ruleId == null)) {
            "Only a global applied condition may omit its rule ID."
        }
    }
}
