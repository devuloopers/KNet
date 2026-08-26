package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePage
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePageQuery
import com.devuloopers.knet.application.contract.traffic.ProtocolMessageQuery
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentationRegistry
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot
import kotlinx.coroutines.flow.Flow

/** Executes one bounded, indexed page query for framed child messages. */
public class QueryProtocolMessagesUseCase(
    private val messages: ProtocolMessageQuery,
) {
    public suspend fun execute(query: ProtocolMessagePageQuery): ProtocolMessagePage =
        messages.queryMessages(query)
}

/** Observes compact invalidations for the framed messages of one selected exchange. */
public class ObserveProtocolMessageChangesUseCase(
    private val messages: ProtocolMessageQuery,
) {
    public fun execute(exchangeId: ExchangeId): Flow<Long> = messages.observeChanges(exchangeId)
}

/** Result of a bounded, lazy framed-message payload read. */
public sealed interface LoadProtocolMessageBodyResult {
    public data class Available(
        public val bytes: ByteArray,
        public val truncated: Boolean,
        public val presentation: ProtocolMessagePresentation? = null,
    ) : LoadProtocolMessageBodyResult {
        init {
            require(bytes.size <= MAXIMUM_PREVIEW_BYTES) { "Protocol message preview exceeds its limit." }
        }

        public companion object {
            public const val MAXIMUM_PREVIEW_BYTES: Int = 1_048_576
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Available

            if (truncated != other.truncated) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (presentation != other.presentation) return false

            return true
        }

        override fun hashCode(): Int {
            var result = truncated.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + (presentation?.hashCode() ?: 0)
            return result
        }
    }

    public data object Empty : LoadProtocolMessageBodyResult
    public data object Unavailable : LoadProtocolMessageBodyResult
}

/** Reads at most one MiB for one selected framed message and never exposes an unrestricted body read. */
public class LoadProtocolMessageBodyUseCase(
    private val messages: ProtocolMessageQuery,
    private val presentations: ProtocolMessagePresentationRegistry,
) {
    public suspend fun execute(
        message: ProtocolMessageSnapshot,
        parentExchange: HttpExchangeSnapshot? = null,
    ): LoadProtocolMessageBodyResult =
        when (val body = message.body) {
            MessageBodyRef.Empty -> LoadProtocolMessageBodyResult.Empty
            is MessageBodyRef.Unavailable -> LoadProtocolMessageBodyResult.Unavailable
            is MessageBodyRef.Available -> {
                val chunk = messages.readBody(
                    bodyId = body.body.id,
                    range = BodyRange(
                        offset = 0L,
                        length = LoadProtocolMessageBodyResult.Available.MAXIMUM_PREVIEW_BYTES,
                    ),
                )
                val bytes = chunk.copyBytes()
                LoadProtocolMessageBodyResult.Available(
                    bytes = bytes,
                    truncated = !chunk.endOfBody,
                    presentation = parentExchange?.let { parent ->
                        presentations.decode(
                            ProtocolMessagePayloadInput(
                                parentExchange = parent,
                                message = message,
                                payload = bytes,
                            ),
                        )
                    },
                )
            }
        }
}
