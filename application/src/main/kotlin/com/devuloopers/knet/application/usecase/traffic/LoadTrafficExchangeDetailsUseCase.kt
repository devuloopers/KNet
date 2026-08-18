package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.MessageBodyRef

/** Maximum byte count loaded for one request or response preview. */
@JvmInline
public value class BodyPreviewLimit(public val bytes: Int) {
    init {
        require(bytes in 1..1_048_576) { "Body preview limit must be between 1 and 1048576 bytes." }
    }

    public companion object {
        /** Default body preview limit of one mebibyte per message. */
        public val Default: BodyPreviewLimit = BodyPreviewLimit(1_048_576)
    }
}

/** Result of loading one bounded request or response body preview. */
public sealed interface TrafficBodyPreview {
    /** The message has no body. */
    public data object Empty : TrafficBodyPreview

    /**
     * Capture metadata says the body is unavailable.
     *
     * @property outcome Capture outcome explaining why bytes are unavailable.
     */
    public data class Unavailable(public val outcome: BodyCaptureOutcome) : TrafficBodyPreview

    /**
     * A bounded body range was loaded.
     *
     * @property chunk Immutable-copy range; [BodyChunk.endOfBody] reports preview truncation.
     */
    public data class Available(public val chunk: BodyChunk) : TrafficBodyPreview

    /** The body reference existed but its backing content could not be read. */
    public data object ReadFailed : TrafficBodyPreview
}

/**
 * Canonical exchange and bounded body previews prepared for a traffic detail presentation.
 *
 * @property exchange Shared immutable exchange snapshot.
 * @property requestBody Bounded request body state.
 * @property responseBody Bounded response body state, or null when no response exists.
 */
public data class TrafficExchangeDetails(
    public val exchange: HttpExchangeSnapshot,
    public val requestBody: TrafficBodyPreview,
    public val responseBody: TrafficBodyPreview?,
)

/** Result of resolving traffic details by exchange identifier. */
public sealed interface LoadTrafficExchangeDetailsResult {
    /**
     * The exchange and bounded previews were resolved.
     *
     * @property details Prepared canonical detail state.
     */
    public data class Found(public val details: TrafficExchangeDetails) : LoadTrafficExchangeDetailsResult

    /** The exchange was absent or removed before the read completed. */
    public data object Missing : LoadTrafficExchangeDetailsResult
}

/**
 * Loads a canonical exchange plus bounded body previews through [TrafficQueryPort].
 *
 * The use case never exposes storage paths and never requests more than [BodyPreviewLimit] for
 * either message. A missing body is isolated from the exchange metadata so the inspector can
 * continue to display headers and timings.
 *
 * @property trafficQueryPort Indexed traffic and body-access boundary.
 */
public class LoadTrafficExchangeDetailsUseCase(
    private val trafficQueryPort: TrafficQueryPort,
) {
    /**
     * Resolves one exchange and its first bounded body ranges.
     *
     * @param exchangeId Stable exchange identifier.
     * @param previewLimit Maximum bytes requested for each message.
     * @return Found detail state or [LoadTrafficExchangeDetailsResult.Missing].
     */
    public suspend fun execute(
        exchangeId: ExchangeId,
        previewLimit: BodyPreviewLimit = BodyPreviewLimit.Default,
    ): LoadTrafficExchangeDetailsResult {
        val exchange = trafficQueryPort.getExchange(exchangeId)
            ?: return LoadTrafficExchangeDetailsResult.Missing
        val requestBody = loadPreview(exchange.request.body, previewLimit)
        val responseBody = exchange.response?.let { response ->
            loadPreview(response.body, previewLimit)
        }
        return LoadTrafficExchangeDetailsResult.Found(
            TrafficExchangeDetails(
                exchange = exchange,
                requestBody = requestBody,
                responseBody = responseBody,
            ),
        )
    }

    private suspend fun loadPreview(
        body: MessageBodyRef,
        previewLimit: BodyPreviewLimit,
    ): TrafficBodyPreview = when (body) {
        MessageBodyRef.Empty -> TrafficBodyPreview.Empty
        is MessageBodyRef.Unavailable -> TrafficBodyPreview.Unavailable(body.outcome)
        is MessageBodyRef.Available -> {
            try {
                TrafficBodyPreview.Available(
                    trafficQueryPort.readBody(
                        bodyId = body.body.id,
                        range = BodyRange(offset = 0L, length = previewLimit.bytes),
                    ),
                )
            } catch (_: IllegalStateException) {
                TrafficBodyPreview.ReadFailed
            }
        }
    }
}
