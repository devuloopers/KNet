package com.devuloopers.knet.domain.traffic.model

/**
 * On-demand body payload container loaded lazily when the user opens the inspector for a transaction.
 *
 * Request and response headers are included so the body decoder can apply the correct
 * Content-Encoding decompression (Gzip, Brotli, Deflate, Zstd) without needing the
 * full [com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction].
 *
 * @property requestBody Raw byte array of the request body payload, or null if absent.
 * @property requestHeaders Request headers list for Content-Type and Content-Encoding lookup.
 * @property responseBody Raw byte array of the response body payload, or null if absent.
 * @property responseHeaders Response headers list for Content-Type and Content-Encoding lookup.
 */
data class TransactionBody(
    val requestBody: ByteArray?,
    val requestHeaders: List<Pair<String, String>>,
    val responseBody: ByteArray?,
    val responseHeaders: List<Pair<String, String>>
) {
    companion object {
        /** Empty sentinel returned when the transaction body files cannot be resolved. */
        val Empty = TransactionBody(
            requestBody = null,
            requestHeaders = emptyList(),
            responseBody = null,
            responseHeaders = emptyList()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionBody) return false
        if (!requestBody.contentEquals(other.requestBody ?: ByteArray(0))) return false
        if (!responseBody.contentEquals(other.responseBody ?: ByteArray(0))) return false
        return requestHeaders == other.requestHeaders && responseHeaders == other.responseHeaders
    }

    override fun hashCode(): Int {
        var result = requestBody?.contentHashCode() ?: 0
        result = 31 * result + (responseBody?.contentHashCode() ?: 0)
        result = 31 * result + requestHeaders.hashCode()
        result = 31 * result + responseHeaders.hashCode()
        return result
    }
}
