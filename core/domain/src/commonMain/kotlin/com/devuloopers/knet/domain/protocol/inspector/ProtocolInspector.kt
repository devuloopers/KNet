package com.devuloopers.knet.domain.protocol.inspector

import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata

/**
 * Scalable strategy interface for detecting specialized protocol types from intercepted network traffic.
 *
 * Implementations inspect method, target URL, headers, and request body bytes to return strongly-typed
 * [InterceptionMetadata] when a matching protocol pattern is identified.
 */
public interface ProtocolInspector {

    /**
     * Priority evaluation order for the inspector (higher values are evaluated first).
     */
    public val priority: Int

    /**
     * Inspects intercepted request metadata and body bytes to determine if a specialized protocol matches.
     *
     * @param method HTTP request method string (e.g. "POST", "GET").
     * @param url Full target URL string.
     * @param headers Key-value map of HTTP headers.
     * @param bodyBytes Raw request body byte array.
     * @return [InterceptionMetadata] if the protocol matches, or null if unhandled.
     */
    public fun inspect(
        method: String,
        url: String,
        headers: Map<String, String>,
        bodyBytes: ByteArray
    ): InterceptionMetadata?
}
