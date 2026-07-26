package com.devuloopers.knet.controller

import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.livetraffic.model.UriDetails
import com.devuloopers.knet.model.HttpTransaction
import com.devuloopers.knet.domain.utils.decodeBodyToText
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

/**
 * Extension function mapping a domain [HttpTransaction] entity to a presentation [TransactionUiModel].
 *
 * @param sequentialId The numerical index for list ordering.
 * @return Formatted [TransactionUiModel].
 */
fun HttpTransaction.toUiModel(sequentialId: Int): TransactionUiModel {
    val uriDetails = UriDetails.parse(request.url)
    val host = uriDetails.host
    val path = uriDetails.path
    val queryParams = uriDetails.queryParams

    // 3. Format Date Group
    val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    val dateGroup = formatter.format(Date(timestamp))

    // 4. Format Payload Size string
    val sizeText = if (response == null) {
        "-"
    } else {
        val reqLen = request.body?.size ?: 0
        val resLen = response?.body?.size ?: 0
        val totalBytes = reqLen + resLen
        when {
            totalBytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", totalBytes / (1024.0 * 1024.0))
            totalBytes >= 1024 -> String.format(Locale.US, "%.2f KB", totalBytes / 1024.0)
            else -> "$totalBytes B"
        }
    }

    // 5. Decode body bytes — handles binary detection, Brotli/gzip decompression,
    //    charset extraction, and JSON pretty-printing in one unified pipeline.
    val reqBodyText = decodeBodyToText(request.body, request.headers)
    val resBodyText = decodeBodyToText(response?.body, response?.headers ?: emptyList())

    val scheme = try {
        URI(request.url).scheme ?: "https"
    } catch (_: Exception) {
        "https"
    }
    val reqHeadersMap = request.headers.toMap() + (":scheme" to scheme) + (":version" to request.protocol)
    val resHeadersMap = response?.headers?.toMap() ?: emptyMap()

    return TransactionUiModel(
        id = sequentialId,
        method = request.method,
        host = host,
        path = path,
        status = response?.statusCode ?: 0,
        statusText = response?.statusText ?: "Active",
        time = if (response == null) "-" else "$durationMs ms",
        size = sizeText,
        dateGroup = dateGroup,
        requestBody = reqBodyText,
        responseBody = resBodyText,
        queryParams = queryParams,
        requestHeaders = reqHeadersMap,
        responseHeaders = resHeadersMap,
        timings = timings
    )
}
