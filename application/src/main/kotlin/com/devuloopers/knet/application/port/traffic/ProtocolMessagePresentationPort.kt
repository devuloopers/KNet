package com.devuloopers.knet.application.port.traffic

import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot

/** Owned message payload passed to a protocol-specific presentation decoder. */
public data class ProtocolMessagePayloadInput(
    public val parentExchange: HttpExchangeSnapshot,
    public val message: ProtocolMessageSnapshot,
    public val payload: ByteArray,
) {
    init {
        require(payload.size <= MAXIMUM_PAYLOAD_BYTES) { "Protocol message decoder payload exceeds its limit." }
    }

    public companion object {
        public const val MAXIMUM_PAYLOAD_BYTES: Int = 1_048_576
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProtocolMessagePayloadInput

        if (parentExchange != other.parentExchange) return false
        if (message != other.message) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = parentExchange.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/** Small protocol-owned presentation document consumed by generic Traffic and breakpoint UIs. */
public data class ProtocolMessagePresentation(
    public val title: String,
    public val contentType: String,
    public val text: String,
    public val schemaName: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Protocol message presentation title must not be blank." }
        require(contentType.isNotBlank()) { "Protocol message presentation content type must not be blank." }
    }
}

/** Additive decoder SPI implemented by protocol engines, never by Traffic UI. */
public interface ProtocolMessagePayloadDecoder {
    /** Stable composition identity used to reject accidental duplicate registrations. */
    public val decoderId: ProtocolMessagePayloadDecoderId

    public val protocolId: MessageProtocolId

    /** Larger values run first when several semantic decoders share one transport protocol. */
    public val priority: Int

    public fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation?
}

/** Stable normalized identity for one protocol-message presentation decoder. */
@JvmInline
public value class ProtocolMessagePayloadDecoderId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Protocol message decoder ID must not be blank." }
        require(value == value.trim().lowercase()) {
            "Protocol message decoder ID must be a normalized lowercase token."
        }
    }
}

/** Immutable validated decoder registry assembled by the product composition root. */
public class ProtocolMessagePresentationRegistry(
    decoders: List<ProtocolMessagePayloadDecoder> = emptyList(),
) {
    private val decodersByProtocol = decoders
        .also { contributions ->
            require(contributions.distinctBy(ProtocolMessagePayloadDecoder::decoderId).size == contributions.size) {
                "Protocol message decoder IDs must be unique."
            }
        }
        .groupBy(ProtocolMessagePayloadDecoder::protocolId)
        .mapValues { (_, contributions) ->
            contributions.sortedWith(
                compareByDescending<ProtocolMessagePayloadDecoder>(ProtocolMessagePayloadDecoder::priority)
                    .thenBy { decoder -> decoder.decoderId.value },
            )
        }

    /** Resolves the first confident semantic presentation, then falls through to transport decoders. */
    public fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation? {
        for (decoder in decodersByProtocol[input.message.protocol].orEmpty()) {
            decoder.decode(input)?.let { presentation -> return presentation }
        }
        return null
    }
}
