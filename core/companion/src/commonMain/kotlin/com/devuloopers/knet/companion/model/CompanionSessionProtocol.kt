package com.devuloopers.knet.companion.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Closed control vocabulary, separate from captured application WebSocket messages. */
@Serializable
public enum class CompanionSessionMessageKind {
    /** Starts a VPN-owned authenticated channel. */ START,
    /** Renews the active VPN presence lease. */ HEARTBEAT,
    /** Explicitly ends the current VPN run. */ STOP,
    /** Confirms protocol admission before policy delivery. */ WELCOME,
    /** Complete authoritative packet-policy snapshot. */ CONFIGURATION,
    /** Renews only an already applied policy revision. */ POLICY_RENEWAL,
    /** Reports the native owner's actual application outcome. */ POLICY_ACK,
    /** Ends a no-longer-authorized channel. */ REVOKED,
}

/** Native policy owner acknowledgement; transport receipt alone is never reported as applied. */
@Serializable
public enum class CompanionPolicyAcknowledgement { APPLIED, INACTIVE, UNSUPPORTED, FAILED }

/**
 * Small versioned control envelope. Authentication belongs to the pinned WebSocket handshake, not these fields.
 * The VPN run and sequence fence late frames, while revision fences stale policy updates after reconnect.
 */
@Serializable
public data class CompanionSessionMessage(
    /** Closed operation code checked against the allowed direction. */
    public val kind: CompanionSessionMessageKind,
    /** Opaque identifier of the active VPN run; never a device authenticator. */
    public val runId: String,
    /** Monotonic client event order within this channel. */
    public val sequence: Long = 0L,
    /** Monotonic configuration revision within the authenticated server session. */
    public val revision: Long = 0L,
    /** Included only in a complete authoritative configuration snapshot. */
    public val configuration: PacketConditionConfiguration? = null,
    /** Included only when the native owner acknowledges a revision. */
    public val acknowledgement: CompanionPolicyAcknowledgement? = null,
    /** Strict protocol version; future versions require explicit negotiation. */
    public val version: Int = CompanionSessionProtocol.VERSION,
) {
    init {
        require(version == CompanionSessionProtocol.VERSION)
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(sequence >= 0L && revision >= 0L)
        require((kind == CompanionSessionMessageKind.CONFIGURATION) == (configuration != null))
        require((kind == CompanionSessionMessageKind.POLICY_ACK) == (acknowledgement != null))
        require(kind !in setOf(CompanionSessionMessageKind.CONFIGURATION, CompanionSessionMessageKind.POLICY_RENEWAL,
            CompanionSessionMessageKind.POLICY_ACK) || revision > 0L)
    }
}

/** Strict bounded JSON codec shared by desktop and native Companion control adapters. */
public object CompanionSessionProtocol {
    /** Supported protocol version. */
    public const val VERSION: Int = 1
    /** Authenticated route on the existing HTTPS control endpoint. */
    public const val PATH: String = "/companion/session"
    /** Bound applies to encoded messages as well as WebSocket framing. */
    public const val MAXIMUM_MESSAGE_BYTES: Int = 8192
    /** Policy authorization must renew before the existing five-second safety lease expires. */
    public const val POLICY_RENEWAL_MILLIS: Long = 2000L
    /** Existing packet-policy safety lease; independent from device-presence expiry. */
    public const val POLICY_LEASE_MILLIS: Long = 5000L
    /** VPN lifecycle application heartbeat; transport ping/pong cannot replace it. */
    public const val HEARTBEAT_MILLIS: Long = 15_000L

    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    /** Encodes validated fields and enforces a strict UTF-8 byte budget. */
    public fun encode(message: CompanionSessionMessage): String = json.encodeToString(message).also { encoded ->
        require(encoded.encodeToByteArray().size <= MAXIMUM_MESSAGE_BYTES)
    }

    /** Decodes strict UTF-8/JSON; unknown fields, versions and inconsistent message shapes are rejected. */
    public fun decode(text: String): CompanionSessionMessage {
        require(text.encodeToByteArray().size in 1..MAXIMUM_MESSAGE_BYTES)
        val message = json.decodeFromString<CompanionSessionMessage>(text)
        requireUniqueKeys(text)
        return message
    }

    // Decode validates syntax first. Inspect the bounded original text so duplicate keys cannot be hidden
    // by JsonObject's last-value semantics, including escaped aliases and nested policy fields.
    private fun requireUniqueKeys(text: String) {
        val objects = mutableListOf<MutableSet<String>>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{' -> objects.add(mutableSetOf())
                '}' -> objects.removeAt(objects.lastIndex)
                '"' -> {
                    val start = index++
                    while (index < text.length) {
                        if (text[index] == '\\') index += 2
                        else if (text[index++] == '"') break
                    }
                    val end = index
                    while (index < text.length && text[index].isWhitespace()) index++
                    if (index < text.length && text[index] == ':') {
                        require(objects.last().add(json.decodeFromString<String>(text.substring(start, end))))
                    }
                    continue
                }
            }
            index++
        }
    }

    /** Messages allowed from an authenticated VPN client. */
    public fun allowsClient(kind: CompanionSessionMessageKind): Boolean = kind in setOf(
        CompanionSessionMessageKind.START, CompanionSessionMessageKind.HEARTBEAT,
        CompanionSessionMessageKind.STOP, CompanionSessionMessageKind.POLICY_ACK,
    )
}
