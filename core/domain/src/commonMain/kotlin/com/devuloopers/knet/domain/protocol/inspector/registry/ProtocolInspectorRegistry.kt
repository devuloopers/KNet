package com.devuloopers.knet.domain.protocol.inspector.registry

import com.devuloopers.knet.domain.protocol.inspector.ProtocolInspector
import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata

/**
 * Thread-safe central registry evaluating registered [ProtocolInspector] strategies against intercepted traffic.
 *
 * Evaluates inspectors in priority order (highest priority evaluated first). Falls back to
 * [InterceptionMetadata.GenericHttp] if no specialized protocol inspector matches.
 *
 * @param inspectors Registered list of protocol inspectors.
 */
class ProtocolInspectorRegistry(
    inspectors: List<ProtocolInspector> = emptyList()
) {

    private val sortedInspectors: List<ProtocolInspector> = inspectors.sortedByDescending { it.priority }

    /**
     * Inspects network transaction details and returns the matching [InterceptionMetadata].
     *
     * @param method HTTP request method string (e.g. "POST", "GET").
     * @param url Target URL string.
     * @param headers Key-value map of HTTP headers.
     * @param bodyBytes Raw request body byte array.
     * @return [InterceptionMetadata] derived from matching protocol inspector or [InterceptionMetadata.GenericHttp].
     */
    fun inspect(
        method: String,
        url: String,
        headers: Map<String, String>,
        bodyBytes: ByteArray
    ): InterceptionMetadata {
        for (inspector in sortedInspectors) {
            val metadata = inspector.inspect(method, url, headers, bodyBytes)
            if (metadata != null) {
                return metadata
            }
        }
        return InterceptionMetadata.GenericHttp
    }
}
