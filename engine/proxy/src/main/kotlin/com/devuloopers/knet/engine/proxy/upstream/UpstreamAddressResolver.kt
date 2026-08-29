package com.devuloopers.knet.engine.proxy.upstream

import com.devuloopers.knet.core.logger.KNetLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

/** Network family retained explicitly so connection policy never depends on address-string parsing. */
internal enum class UpstreamAddressFamily {
    IPV4,
    IPV6,
}

/** One resolved, numeric upstream socket candidate. */
internal data class UpstreamAddressCandidate(
    val socketAddress: InetSocketAddress,
    val family: UpstreamAddressFamily,
) {
    init {
        require(!socketAddress.isUnresolved) { "Upstream address candidates must already be resolved." }
    }
}

/** All bounded candidates for one routing hostname and port. */
internal data class ResolvedUpstreamRoute(
    val host: String,
    val port: Int,
    val candidates: List<UpstreamAddressCandidate>,
) {
    init {
        require(host.isNotBlank()) { "Upstream route host must not be blank." }
        require(port in 1..65_535) { "Upstream route port must be between 1 and 65535." }
        require(candidates.isNotEmpty()) { "An upstream route must contain at least one address candidate." }
        require(candidates.all { candidate -> candidate.socketAddress.port == port }) {
            "Every upstream address candidate must use the route port."
        }
    }

    /** Stable order-independent identity used to prevent unsafe HTTP/2 route reuse after DNS changes. */
    val addressSetKey: List<String> = candidates
        .map { candidate -> candidate.socketAddress.address.hostAddress }
        .distinct()
        .sorted()
}

/** Asynchronously resolves every usable address for one upstream routing hostname. */
internal fun interface UpstreamAddressResolver {
    fun resolve(
        host: String,
        port: Int,
        fallbackDnsHost: String?,
    ): CompletableFuture<ResolvedUpstreamRoute>
}

/** JVM resolver that moves blocking platform DNS lookup away from Netty event-loop threads. */
internal class CoroutineUpstreamAddressResolver(
    private val scope: CoroutineScope,
    private val maximumCandidates: Int,
    private val lookup: (String) -> Array<InetAddress> = InetAddress::getAllByName,
) : UpstreamAddressResolver {

    init {
        require(maximumCandidates > 0) { "Maximum upstream address candidates must be positive." }
    }

    override fun resolve(
        host: String,
        port: Int,
        fallbackDnsHost: String?,
    ): CompletableFuture<ResolvedUpstreamRoute> {
        require(host.isNotBlank()) { "Upstream route host must not be blank." }
        require(port in 1..65_535) { "Upstream route port must be between 1 and 65535." }
        val result = CompletableFuture<ResolvedUpstreamRoute>()
        val resolution = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val primaryAddresses = lookup(host)
                        .asSequence()
                        .filter { address -> address is Inet4Address || address is Inet6Address }
                        .distinct()
                        .toList()
                    require(primaryAddresses.isNotEmpty()) { "DNS returned no usable addresses for $host." }
                    val fallbackAddresses = fallbackDnsHost
                        ?.takeUnless { fallback -> fallback.equals(host, ignoreCase = true) }
                        ?.let { fallback ->
                            runCatching {
                                lookup(fallback)
                                    .asSequence()
                                    .filter { address -> address is Inet4Address || address is Inet6Address }
                                    .distinct()
                                    .toList()
                            }.getOrElse { failure ->
                                KNetLogger.debug(UPSTREAM_RESOLUTION_TAG) {
                                    "upstream_event=fallback_dns_failed host=$fallback " +
                                        "reason=${failure.javaClass.simpleName}:${failure.message.orEmpty()}"
                                }
                                emptyList()
                            }
                        }
                        .orEmpty()
                    ResolvedUpstreamRoute(
                        host = host,
                        port = port,
                        candidates = interleaveAddressFamilies(primaryAddresses + fallbackAddresses)
                            .distinct()
                            .take(maximumCandidates)
                            .map { address ->
                                UpstreamAddressCandidate(
                                    socketAddress = InetSocketAddress(address, port),
                                    family = when (address) {
                                        is Inet6Address -> UpstreamAddressFamily.IPV6
                                        else -> UpstreamAddressFamily.IPV4
                                    },
                                )
                            },
                    ).also { route ->
                        KNetLogger.debug(UPSTREAM_RESOLUTION_TAG) {
                            "upstream_event=dns_resolved host=${route.host} port=${route.port} " +
                                "fallback_host=${fallbackDnsHost.orEmpty()} " +
                                "candidates=${route.candidates.joinToString(",") { candidate ->
                                    "${candidate.family}:${candidate.socketAddress.address.hostAddress}"
                                }}"
                        }
                    }
                }
            }.fold(
                onSuccess = result::complete,
                onFailure = { failure ->
                    KNetLogger.warn(UPSTREAM_RESOLUTION_TAG) {
                        "upstream_event=dns_failed host=$host port=$port " +
                            "reason=${failure.javaClass.simpleName}:${failure.message.orEmpty()}"
                    }
                    result.completeExceptionally(failure)
                },
            )
        }
        result.whenComplete { _, _ ->
            if (result.isCancelled) resolution.cancel()
        }
        return result
    }
}

private const val UPSTREAM_RESOLUTION_TAG: String = "ProxyEngine"

/** Preserves platform preference while placing the alternate family second instead of last. */
internal fun interleaveAddressFamilies(addresses: List<InetAddress>): List<InetAddress> {
    if (addresses.size < 2) return addresses
    val preferredFamilyIsIpv6 = addresses.first() is Inet6Address
    val preferred = ArrayDeque(addresses.filter { address -> (address is Inet6Address) == preferredFamilyIsIpv6 })
    val alternate = ArrayDeque(addresses.filter { address -> (address is Inet6Address) != preferredFamilyIsIpv6 })
    return buildList(addresses.size) {
        while (preferred.isNotEmpty() || alternate.isNotEmpty()) {
            preferred.removeFirstOrNull()?.let(::add)
            alternate.removeFirstOrNull()?.let(::add)
        }
    }
}
