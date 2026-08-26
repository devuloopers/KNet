package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.absoluteUrl

/** Result of converting one bounded canonical capture into KNet's shared authored-request contract. */
public sealed interface PrepareCapturedNetworkRequestResult {
    /** Captured request conversion completed without truncating its body. */
    public data class Found(public val spec: NetworkRequestSpec) : PrepareCapturedNetworkRequestResult

    /** The exchange was removed or did not exist. */
    public data object Missing : PrepareCapturedNetworkRequestResult

    /** The captured body exceeds the explicit whole-body export budget. */
    public data class BodyTooLarge(public val observedBytes: Long, public val limitBytes: Int) :
        PrepareCapturedNetworkRequestResult

    /** The body metadata exists but its bytes could not be loaded. */
    public data object BodyUnavailable : PrepareCapturedNetworkRequestResult

    /** The canonical request does not contain an absolute HTTP or HTTPS target. */
    public data object IncompleteTarget : PrepareCapturedNetworkRequestResult
}

/**
 * Converts a canonical captured request into the shared [NetworkRequestSpec] used by API Studio,
 * replay, collections, and export.
 *
 * Conversion remains outside presentation so URL rendering, ordered headers, repeated query pairs,
 * body ownership, and decoding semantics have one implementation.
 *
 * @property prepareTrafficRequest Loads canonical metadata and complete body content under a bound.
 */
public class PrepareCapturedNetworkRequestUseCase(
    private val prepareTrafficRequest: PrepareTrafficRequestUseCase,
) {
    /**
     * Resolves and converts [exchangeId] without silently truncating body content.
     *
     * @param exchangeId Stable canonical exchange identifier.
     * @param bodyLimitBytes Maximum whole-body byte count accepted for cross-feature transfer.
     * @return Typed conversion result.
     */
    public suspend fun execute(
        exchangeId: ExchangeId,
        bodyLimitBytes: Int = PrepareTrafficRequestUseCase.DEFAULT_BODY_LIMIT_BYTES,
    ): PrepareCapturedNetworkRequestResult {
        return when (val prepared = prepareTrafficRequest.execute(exchangeId, bodyLimitBytes)) {
            is PrepareTrafficRequestResult.Found -> prepared.value.toNetworkRequestResult()
            PrepareTrafficRequestResult.Missing -> PrepareCapturedNetworkRequestResult.Missing
            PrepareTrafficRequestResult.BodyUnavailable -> PrepareCapturedNetworkRequestResult.BodyUnavailable
            is PrepareTrafficRequestResult.BodyTooLarge -> PrepareCapturedNetworkRequestResult.BodyTooLarge(
                observedBytes = prepared.observedBytes,
                limitBytes = prepared.limitBytes,
            )
        }
    }

    private fun PreparedTrafficRequest.toNetworkRequestResult(): PrepareCapturedNetworkRequestResult {
        val url = request.absoluteUrl()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return PrepareCapturedNetworkRequestResult.IncompleteTarget
        }
        val headers = request.head.headers.map { header -> header.name.value to header.value }
        val bodyBytes = ByteArray(bodyChunks.sumOf { chunk -> chunk.size })
        var destinationOffset = 0
        bodyChunks.forEach { chunk ->
            val bytes = chunk.copyBytes()
            bytes.copyInto(bodyBytes, destinationOffset = destinationOffset)
            destinationOffset += bytes.size
        }
        return PrepareCapturedNetworkRequestResult.Found(
            NetworkRequestSpec(
                method = request.head.method,
                httpVersionPreference = HttpVersionPreference.fromProtocol(request.head.protocol),
                url = url,
                headers = headers,
                queryParams = UrlQueryStringParser.parseQueryParams(url),
                bodyPayload = decodeBodyToText(bodyBytes.takeIf { bytes -> bytes.isNotEmpty() }, headers),
                timestamp = startedAtEpochMillis,
            ),
        )
    }
}
