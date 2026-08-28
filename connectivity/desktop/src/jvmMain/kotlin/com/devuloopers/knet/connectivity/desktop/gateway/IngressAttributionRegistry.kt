package com.devuloopers.knet.connectivity.desktop.gateway

import kotlin.time.Clock
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
import com.devuloopers.knet.traffic.model.IngressAttributionRegistration
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import java.util.concurrent.ConcurrentHashMap

/** Bounded, expiring, one-shot bridge-socket attribution registry. */
public class IngressAttributionRegistry(
    private val maximumEntries: Int = 4_096,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : IngressAttributionLookup, IngressAttributionRegistration {
    private data class Entry(val context: IngressContext, val expiresAt: Long)
    private val entries = ConcurrentHashMap<String, Entry>()

    init { require(maximumEntries in 1..65_536) }

    override fun register(
        downstream: TrafficEndpoint,
        context: IngressContext,
        expiresAtEpochMillis: Long,
    ): Boolean {
        val now = nowMillis()
        if (expiresAtEpochMillis <= now) return false
        if (entries.size >= maximumEntries) purgeExpired(now)
        if (entries.size >= maximumEntries) return false
        return entries.putIfAbsent(downstream.key(), Entry(context, expiresAtEpochMillis)) == null
    }

    override fun claim(downstream: TrafficEndpoint): IngressContext? {
        val entry = entries.remove(downstream.key()) ?: return null
        return entry.context.takeIf { entry.expiresAt > nowMillis() }
    }

    private fun purgeExpired(now: Long) {
        entries.entries.removeIf { it.value.expiresAt <= now }
    }

    private fun TrafficEndpoint.key(): String = "${host.lowercase()}:$port"
}
