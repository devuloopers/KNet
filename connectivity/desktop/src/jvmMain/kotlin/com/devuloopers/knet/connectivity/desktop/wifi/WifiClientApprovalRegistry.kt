package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.connectivity.model.WifiApprovedClient
import com.devuloopers.knet.connectivity.model.WifiClientCandidateId
import com.devuloopers.knet.connectivity.model.WifiClientId
import com.devuloopers.knet.connectivity.model.WifiPendingClient
import java.security.SecureRandom
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Immutable registry snapshot supplied to the owning runtime after each meaningful transition. */
internal data class WifiClientApprovalSnapshot(
    val pendingClients: List<WifiPendingClient>,
    val approvedClients: List<WifiApprovedClient>,
)

/** Session-owned source approval registry; approvals never survive disable or network change. */
@OptIn(ExperimentalUuidApi::class)
internal class WifiClientApprovalRegistry(
    private val nowMillis: () -> Long,
    private val pendingLifetimeMillis: Long = DEFAULT_PENDING_LIFETIME_MILLIS,
    private val maximumPendingClients: Int = DEFAULT_MAXIMUM_PENDING_CLIENTS,
    private val maximumApprovedClients: Int = DEFAULT_MAXIMUM_APPROVED_CLIENTS,
    private val random: SecureRandom = SecureRandom(),
    private val onChanged: (WifiClientApprovalSnapshot) -> Unit = {},
) {
    private val lock = Any()
    private val pendingById = LinkedHashMap<WifiClientCandidateId, WifiPendingClient>()
    private val pendingIdBySource = mutableMapOf<String, WifiClientCandidateId>()
    private val approvedById = LinkedHashMap<WifiClientId, WifiApprovedClient>()
    private val approvedIdBySource = mutableMapOf<String, WifiClientId>()

    init {
        require(pendingLifetimeMillis in 1_000L..MAXIMUM_PENDING_LIFETIME_MILLIS)
        require(maximumPendingClients in 1..MAXIMUM_CONFIGURABLE_CLIENTS)
        require(maximumApprovedClients in 1..MAXIMUM_CONFIGURABLE_CLIENTS)
    }

    fun observe(sourceAddress: String): WifiPendingClient? {
        val normalized = sourceAddress.trim().takeIf(String::isNotBlank) ?: return null
        var changed = false
        val candidate = synchronized(lock) {
            changed = purgeExpired(nowMillis())
            if (approvedIdBySource.containsKey(normalized)) return@synchronized null
            pendingIdBySource[normalized]?.let(pendingById::get)?.let { return@synchronized it }
            if (pendingById.size >= maximumPendingClients) return@synchronized null
            val now = nowMillis()
            val created = WifiPendingClient(
                id = WifiClientCandidateId(Uuid.random().toString()),
                sourceAddress = normalized,
                requestedAtEpochMillis = now,
                expiresAtEpochMillis = now + pendingLifetimeMillis,
                confirmationCode = random.nextInt(CONFIRMATION_CODE_BOUND).toString().padStart(6, '0'),
            )
            pendingById[created.id] = created
            pendingIdBySource[normalized] = created.id
            changed = true
            created
        }
        if (changed) notifyChanged()
        return candidate
    }

    fun approve(candidateId: WifiClientCandidateId, displayName: String): WifiApprovedClient? {
        val normalizedName = displayName.trim().takeIf { it.isNotEmpty() && it.length <= MAXIMUM_DISPLAY_NAME_LENGTH }
            ?: return null
        val approved = synchronized(lock) {
            purgeExpired(nowMillis())
            if (approvedById.size >= maximumApprovedClients) return@synchronized null
            val candidate = pendingById.remove(candidateId) ?: return@synchronized null
            pendingIdBySource.remove(candidate.sourceAddress, candidateId)
            approvedIdBySource.remove(candidate.sourceAddress)?.let(approvedById::remove)
            WifiApprovedClient(
                id = WifiClientId(Uuid.random().toString()),
                displayName = normalizedName,
                sourceAddress = candidate.sourceAddress,
                approvedAtEpochMillis = nowMillis(),
            ).also { client ->
                approvedById[client.id] = client
                approvedIdBySource[client.sourceAddress] = client.id
            }
        }
        if (approved != null) notifyChanged()
        return approved
    }

    fun reject(candidateId: WifiClientCandidateId): Boolean {
        val removed = synchronized(lock) {
            val candidate = pendingById.remove(candidateId) ?: return@synchronized false
            pendingIdBySource.remove(candidate.sourceAddress, candidateId)
            true
        }
        if (removed) notifyChanged()
        return removed
    }

    fun approvedFor(sourceAddress: String): WifiApprovedClient? = synchronized(lock) {
        approvedIdBySource[sourceAddress]?.let(approvedById::get)
    }

    fun revoke(clientId: WifiClientId): WifiApprovedClient? {
        val removed = synchronized(lock) {
            val client = approvedById.remove(clientId) ?: return@synchronized null
            approvedIdBySource.remove(client.sourceAddress, clientId)
            client
        }
        notifyChanged()
        return removed
    }

    fun snapshot(): WifiClientApprovalSnapshot {
        var purged = false
        val snapshot = synchronized(lock) {
            purged = purgeExpired(nowMillis())
            snapshotLocked()
        }
        if (purged) onChanged(snapshot)
        return snapshot
    }

    fun clear() {
        val changed = synchronized(lock) {
            val hadValues = pendingById.isNotEmpty() || approvedById.isNotEmpty()
            pendingById.clear()
            pendingIdBySource.clear()
            approvedById.clear()
            approvedIdBySource.clear()
            hadValues
        }
        if (changed) notifyChanged()
    }

    private fun purgeExpired(now: Long): Boolean {
        val expired = pendingById.values.filter { it.expiresAtEpochMillis <= now }
        expired.forEach { candidate ->
            pendingById.remove(candidate.id)
            pendingIdBySource.remove(candidate.sourceAddress, candidate.id)
        }
        return expired.isNotEmpty()
    }

    private fun notifyChanged() {
        onChanged(synchronized(lock) { snapshotLocked() })
    }

    private fun snapshotLocked(): WifiClientApprovalSnapshot = WifiClientApprovalSnapshot(
        pendingClients = pendingById.values.sortedBy(WifiPendingClient::requestedAtEpochMillis),
        approvedClients = approvedById.values.sortedBy(WifiApprovedClient::approvedAtEpochMillis),
    )

    private companion object {
        const val CONFIRMATION_CODE_BOUND: Int = 1_000_000
        const val MAXIMUM_DISPLAY_NAME_LENGTH: Int = 80
        const val DEFAULT_MAXIMUM_PENDING_CLIENTS: Int = 16
        const val DEFAULT_MAXIMUM_APPROVED_CLIENTS: Int = 32
        const val MAXIMUM_CONFIGURABLE_CLIENTS: Int = 256
        const val DEFAULT_PENDING_LIFETIME_MILLIS: Long = 5L * 60L * 1_000L
        const val MAXIMUM_PENDING_LIFETIME_MILLIS: Long = 30L * 60L * 1_000L
    }
}
