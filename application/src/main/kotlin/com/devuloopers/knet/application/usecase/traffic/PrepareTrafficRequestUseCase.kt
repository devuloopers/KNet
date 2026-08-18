package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.body.MessageBodyRef

/**
 * Canonical request prepared for API Studio, replay, collection, or export adapters.
 *
 * The request uses the shared HTTP model. Optional body content is a bounded immutable chunk;
 * callers never receive a storage path or an unbounded read capability.
 */
public data class PreparedTrafficRequest(
    public val request: HttpRequestSnapshot,
    public val startedAtEpochMillis: Long,
    public val bodyChunks: List<BodyChunk>,
)

/** Result of preparing a captured request for another authorized feature. */
public sealed interface PrepareTrafficRequestResult {
    /** The canonical request was resolved. */
    public data class Found(public val value: PreparedTrafficRequest) : PrepareTrafficRequestResult

    /** The exchange was removed or did not exist. */
    public data object Missing : PrepareTrafficRequestResult

    /** The request body exists but exceeds the caller's explicit export budget. */
    public data class BodyTooLarge(public val observedBytes: Long, public val limitBytes: Int) :
        PrepareTrafficRequestResult

    /** The request body metadata exists but its content could not be read. */
    public data object BodyUnavailable : PrepareTrafficRequestResult
}

/**
 * Loads one canonical request and, when present, its body under an explicit whole-body budget.
 *
 * This is the shared bridge used instead of passing a Traffic UI row into API Studio. It refuses
 * silent truncation because replaying a partial payload would change request semantics.
 */
public class PrepareTrafficRequestUseCase(
    private val trafficQuery: TrafficQueryPort,
) {
    /** Resolves an exchange request using at most [bodyLimitBytes] bytes. */
    public suspend fun execute(
        exchangeId: ExchangeId,
        bodyLimitBytes: Int = DEFAULT_BODY_LIMIT_BYTES,
    ): PrepareTrafficRequestResult {
        require(bodyLimitBytes in 1..MAX_BODY_LIMIT_BYTES) {
            "Traffic request body limit must be between 1 and $MAX_BODY_LIMIT_BYTES bytes."
        }
        val exchange = trafficQuery.getExchange(exchangeId)
            ?: return PrepareTrafficRequestResult.Missing
        val bodyChunks = when (val ref = exchange.request.body) {
            MessageBodyRef.Empty -> emptyList()
            is MessageBodyRef.Unavailable -> return PrepareTrafficRequestResult.BodyUnavailable
            is MessageBodyRef.Available -> {
                if (ref.body.observedBytes > bodyLimitBytes) {
                    return PrepareTrafficRequestResult.BodyTooLarge(
                        observedBytes = ref.body.observedBytes,
                        limitBytes = bodyLimitBytes,
                    )
                }
                try {
                    val chunks = mutableListOf<BodyChunk>()
                    var offset = 0L
                    while (true) {
                        val remaining = bodyLimitBytes.toLong() - offset
                        if (remaining <= 0L) return PrepareTrafficRequestResult.BodyUnavailable
                        val chunk = trafficQuery.readBody(
                            ref.body.id,
                            BodyRange(
                                offset = offset,
                                length = minOf(remaining, BODY_RANGE_BYTES.toLong()).toInt(),
                            ),
                        )
                        chunks += chunk
                        offset += chunk.size
                        if (chunk.endOfBody) break
                        if (chunk.size == 0) return PrepareTrafficRequestResult.BodyUnavailable
                    }
                    chunks
                } catch (_: IllegalStateException) {
                    return PrepareTrafficRequestResult.BodyUnavailable
                }
            }
        }
        return PrepareTrafficRequestResult.Found(
            PreparedTrafficRequest(
                request = exchange.request,
                startedAtEpochMillis = exchange.startedAtEpochMillis,
                bodyChunks = bodyChunks,
            ),
        )
    }

    public companion object {
        /** Default maximum replay/export request body of ten mebibytes. */
        public const val DEFAULT_BODY_LIMIT_BYTES: Int = 10 * 1024 * 1024

        /** Hard application ceiling for one prepared request body. */
        public const val MAX_BODY_LIMIT_BYTES: Int = 64 * 1024 * 1024

        private const val BODY_RANGE_BYTES: Int = 1024 * 1024
    }
}
