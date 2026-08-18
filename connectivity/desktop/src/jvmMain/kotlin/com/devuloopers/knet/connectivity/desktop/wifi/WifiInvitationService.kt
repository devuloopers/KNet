package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.connectivity.model.WifiInvitation
import com.devuloopers.knet.connectivity.model.WifiInvitationId
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A valid invitation claim bound to one source address for its remaining lifetime. */
internal data class WifiInvitationClaim(
    val invitationId: WifiInvitationId,
    val sourceAddress: String,
    val expiresAtEpochMillis: Long,
)

/** Bounded, expiring invitation store whose bearer tokens are never exposed through observable state. */
@OptIn(ExperimentalUuidApi::class)
internal class WifiInvitationService(
    private val setupBaseUrl: String,
    private val nowMillis: () -> Long,
    private val invitationLifetimeMillis: Long = DEFAULT_INVITATION_LIFETIME_MILLIS,
    private val maximumInvitations: Int = DEFAULT_MAXIMUM_INVITATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    private data class Entry(
        val id: WifiInvitationId,
        val expiresAtEpochMillis: Long,
        val claimedSourceAddress: String?,
    )

    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(setupBaseUrl.startsWith("http://") || setupBaseUrl.startsWith("https://"))
        require(invitationLifetimeMillis in 1_000L..MAXIMUM_INVITATION_LIFETIME_MILLIS)
        require(maximumInvitations in 1..MAXIMUM_CONFIGURABLE_INVITATIONS)
    }

    fun create(): WifiInvitation = synchronized(entries) {
        val now = nowMillis()
        purgeExpired(now)
        while (entries.size >= maximumInvitations) {
            entries.remove(entries.keys.first())
        }
        val tokenBytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val token = URL_BASE64.encode(tokenBytes)
        val id = WifiInvitationId(Uuid.random().toString())
        val expiresAt = now + invitationLifetimeMillis
        entries[token] = Entry(id, expiresAt, claimedSourceAddress = null)
        WifiInvitation(
            id = id,
            setupUrl = "${setupBaseUrl.trimEnd('/')}/invite/$token",
            expiresAtEpochMillis = expiresAt,
        )
    }

    fun claim(token: String, sourceAddress: String): WifiInvitationClaim? = synchronized(entries) {
        if (!token.matches(SAFE_TOKEN) || sourceAddress.isBlank()) return@synchronized null
        val now = nowMillis()
        purgeExpired(now)
        val entry = entries[token] ?: return@synchronized null
        val claimedSource = entry.claimedSourceAddress
        if (claimedSource != null && claimedSource != sourceAddress) return@synchronized null
        if (claimedSource == null) entries[token] = entry.copy(claimedSourceAddress = sourceAddress)
        WifiInvitationClaim(entry.id, sourceAddress, entry.expiresAtEpochMillis)
    }

    fun invalidateAll() {
        synchronized(entries) { entries.clear() }
    }

    private fun purgeExpired(now: Long) {
        entries.entries.removeIf { it.value.expiresAtEpochMillis <= now }
    }

    private companion object {
        val SAFE_TOKEN: Regex = Regex("[A-Za-z0-9_-]{43}")
        val URL_BASE64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        const val TOKEN_BYTES: Int = 32
        const val DEFAULT_MAXIMUM_INVITATIONS: Int = 16
        const val MAXIMUM_CONFIGURABLE_INVITATIONS: Int = 128
        const val DEFAULT_INVITATION_LIFETIME_MILLIS: Long = 5L * 60L * 1_000L
        const val MAXIMUM_INVITATION_LIFETIME_MILLIS: Long = 30L * 60L * 1_000L
    }
}
