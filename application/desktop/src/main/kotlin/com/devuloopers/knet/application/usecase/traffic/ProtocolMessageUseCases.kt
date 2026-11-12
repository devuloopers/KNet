package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePage
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePageQuery
import com.devuloopers.knet.application.contract.traffic.ProtocolMessageQuery
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentationRegistry
import com.devuloopers.knet.application.contract.traffic.ProtocolMessageContextEntry
import com.devuloopers.knet.application.contract.traffic.ProtocolMessageContextQuery
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState

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
                val truncated = !chunk.endOfBody || body.body.outcome != BodyCaptureOutcome.Complete ||
                    body.body.observedBytes > body.body.storedBytes || message.state == ProtocolMessageState.TRUNCATED
                LoadProtocolMessageBodyResult.Available(
                    bytes = bytes,
                    truncated = truncated,
                    presentation = parentExchange?.takeIf { it.id == message.exchangeId }?.let { parent ->
                        val input = ProtocolMessagePayloadInput(parent, message, bytes,
                            truncated = truncated || message.state != ProtocolMessageState.COMPLETE)
                        val context = input.takeUnless { it.truncated }?.let(presentations::contextQuery)?.takeIf {
                            it.exchangeId == message.exchangeId && it.beforeMessageId == message.id && it.protocol == message.protocol
                        }?.let { query -> readContext(query, message) }.orEmpty()
                        presentations.decode(input.copy(context = context))
                    },
                )
            }
        }

    private suspend fun readContext(query: ProtocolMessageContextQuery,
        selected: ProtocolMessageSnapshot): List<ProtocolMessageContextEntry> {
        // Context is optional enrichment. A storage gap must preserve the selected raw preview,
        // and must prevent the decoder from correlating across that gap.
        return try {
            var remaining = ProtocolMessagePayloadInput.MAXIMUM_PAYLOAD_BYTES
            buildList {
                for (message in messages.queryContext(query).take(query.limit)) {
                    if (message.exchangeId != selected.exchangeId || message.connectionId != selected.connectionId ||
                        message.streamId != selected.streamId || message.id == selected.id ||
                        message.protocol != query.protocol || message.kind != query.kind ||
                        query.direction != null && message.direction != query.direction) break
                    val body = message.body as? MessageBodyRef.Available
                    if (body == null || body.body.outcome != BodyCaptureOutcome.Complete ||
                        body.body.observedBytes > body.body.storedBytes || message.state != ProtocolMessageState.COMPLETE || remaining <= 0) {
                        add(ProtocolMessageContextEntry(message, null, true))
                        break
                    }
                    val range = BodyRange(0, minOf(remaining, ProtocolMessageContextEntry.MAXIMUM_PAYLOAD_BYTES))
                    val chunk = messages.readBody(body.body.id, range)
                    if (chunk.size > range.length || chunk.offset != 0L) break
                    val bytes = chunk.copyBytes()
                    remaining -= bytes.size
                    add(ProtocolMessageContextEntry(message, bytes, !chunk.endOfBody))
                    if (!chunk.endOfBody) break
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }
}
