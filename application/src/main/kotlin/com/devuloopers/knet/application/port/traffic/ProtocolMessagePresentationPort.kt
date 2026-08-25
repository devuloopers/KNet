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
    public val protocolId: MessageProtocolId

    public fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation?
}

/** Immutable validated decoder registry assembled by the product composition root. */
public class ProtocolMessagePresentationRegistry(
    decoders: List<ProtocolMessagePayloadDecoder> = emptyList(),
) {
    private val decodersByProtocol = decoders.associateBy(ProtocolMessagePayloadDecoder::protocolId).also { indexed ->
        require(indexed.size == decoders.size) { "Protocol message decoder IDs must be unique." }
    }

    public fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation? =
        decodersByProtocol[input.message.protocol]?.decode(input)
}
