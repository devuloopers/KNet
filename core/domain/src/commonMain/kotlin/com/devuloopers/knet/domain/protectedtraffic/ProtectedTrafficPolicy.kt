package com.devuloopers.knet.domain.protectedtraffic

/**
 * Pure, deterministic protected-traffic evaluator.
 *
 * More specific application rules precede destination rules, user rules precede built-in
 * compatibility rules, and the original list position is the final stable tie breaker.
 */
class ProtectedTrafficPolicy {
    /**
     * Evaluates [request] against one immutable [configuration].
     *
     * @return The selected action and exact rule/global-default evidence.
     */
    fun evaluate(
        configuration: ProtectedTrafficConfiguration,
        request: ProtectedTrafficRequest,
    ): ProtectedTrafficDecision {
        val normalizedHost = request.destinationHost?.let(::normalizeRequestHost)
        val address = request.destinationAddress?.let(::parseIpAddress)
        val candidate = configuration.rules.asSequence()
            .withIndex()
            .filter { (_, rule) -> rule.enabled }
            .filter { (_, rule) ->
                rule.origin != ProtectedTrafficRuleOrigin.BUILT_IN ||
                    rule.groupId in configuration.enabledBuiltInGroups
            }
            .filter { (_, rule) -> rule.matches(request, normalizedHost, address) }
            .maxWithOrNull(
                compareBy<IndexedValue<ProtectedTrafficRule>>(
                    { it.value.precedence() },
                    { if (it.value.origin == ProtectedTrafficRuleOrigin.USER) 1 else 0 },
                    { -it.index },
                ),
            )
            ?.value

        return if (candidate == null) {
            ProtectedTrafficDecision(
                action = configuration.defaultAction,
                evidence = ProtectedTrafficDecisionEvidence.GlobalDefault,
            )
        } else {
            ProtectedTrafficDecision(
                action = candidate.action,
                evidence = ProtectedTrafficDecisionEvidence.Rule(candidate.id, candidate.groupId),
            )
        }
    }
}

private fun ProtectedTrafficRule.matches(
    request: ProtectedTrafficRequest,
    normalizedHost: String?,
    address: ProtectedIpAddress?,
): Boolean {
    if (sourceApplication != null && sourceApplication.value != request.sourceApplication?.value) return false
    if (port != null && port != request.port) return false
    if (transport != null && transport != request.transport) return false
    if (ipFamily != null && ipFamily != address?.family) return false
    return destination?.matches(normalizedHost, address) ?: true
}

private fun ProtectedDestinationSelector.matches(
    normalizedHost: String?,
    address: ProtectedIpAddress?,
): Boolean = when (this) {
    is ProtectedDestinationSelector.Exact -> if (isIpLiteral) {
        val expected = parseIpAddress(value)
        address != null && expected != null && address.bytes.contentEquals(expected.bytes)
    } else {
        normalizedHost == value
    }

    is ProtectedDestinationSelector.WildcardDomain -> normalizedHost != null &&
        normalizedHost.length > suffix.length + 1 &&
        normalizedHost.endsWith(".$suffix")

    is ProtectedDestinationSelector.Cidr -> {
        val network = parseIpAddress(networkAddress)
        address != null && network != null && family == address.family &&
            address.masked(prefixLength).bytes.contentEquals(network.bytes)
    }
}

/** Encodes additive selector specificity without giving action values hidden priority. */
private fun ProtectedTrafficRule.precedence(): Int {
    var score = if (origin == ProtectedTrafficRuleOrigin.USER) 10_000 else 0
    if (sourceApplication != null) score += 2_000
    score += when (val selector = destination) {
        is ProtectedDestinationSelector.Exact -> 1_000
        is ProtectedDestinationSelector.WildcardDomain -> 500 + selector.suffix.length.coerceAtMost(200)
        is ProtectedDestinationSelector.Cidr -> 500 + selector.prefixLength
        null -> 0
    }
    if (port != null) score += 200
    if (transport != null) score += 20
    if (ipFamily != null) score += 10
    return score
}

private fun normalizeRequestHost(value: String): String? = runCatching {
    val parsed = ProtectedDestinationSelector.parse(value)
    (parsed as? ProtectedDestinationSelector.Exact)?.takeUnless { it.isIpLiteral }?.value
}.getOrNull()
