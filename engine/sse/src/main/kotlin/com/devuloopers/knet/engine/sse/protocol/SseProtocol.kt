package com.devuloopers.knet.engine.sse.protocol

import com.devuloopers.knet.traffic.model.http.HeaderField

/** Stable SSE media-type and header recognition shared by all adapters. */
object SseProtocol {
    /** Returns true only for a normalized `text/event-stream` media type. */
    fun isEventStream(contentType: String?): Boolean =
        contentType?.substringBefore(';')?.trim()?.equals(EVENT_STREAM_MEDIA_TYPE, ignoreCase = true) == true

    /** Finds [name] case-insensitively in ordered canonical headers. */
    fun header(headers: List<HeaderField>, name: String): String? =
        headers.firstOrNull { it.name.value.equals(name, ignoreCase = true) }?.value

    /** Returns true when canonical response headers identify an SSE stream. */
    fun isEventStream(headers: List<HeaderField>): Boolean = isEventStream(header(headers, CONTENT_TYPE))

    const val EVENT_STREAM_MEDIA_TYPE: String = "text/event-stream"
    const val CONTENT_TYPE: String = "content-type"
    const val CONTENT_ENCODING: String = "content-encoding"
}
