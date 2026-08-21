package com.devuloopers.knet.domain.clientNetwork.model

import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol

/**
 * Requested HTTP wire-version policy for one authored client request.
 *
 * [AUTO] delegates protocol selection to the active transport. Exact values are intentionally
 * distinct from the observed response protocol: a server or intermediary can answer using a
 * different compatible version, and KNet reports that version in [ExecutionResult].
 */
enum class HttpVersionPreference(
    /** Stable token used by persistence and transport adapters. */
    val token: String,
    /** Compact label used by request-authoring surfaces. */
    val displayName: String,
) {
    AUTO("AUTO", "Auto"),
    HTTP_1_0("HTTP/1.0", "HTTP/1.0"),
    HTTP_1_1("HTTP/1.1", "HTTP/1.1"),
    HTTP_2("HTTP/2", "HTTP/2");

    companion object {
        /** Resolves persisted values while safely defaulting unknown future values to [AUTO]. */
        fun fromToken(token: String): HttpVersionPreference = entries.firstOrNull {
            it.token.equals(token.trim(), ignoreCase = true) ||
                it.name.equals(token.trim(), ignoreCase = true)
        } ?: AUTO

        /** Maps an observed protocol to the closest executable preference currently supported. */
        fun fromProtocol(protocol: ApplicationProtocol): HttpVersionPreference = when (protocol) {
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_0) -> HTTP_1_0
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1) -> HTTP_1_1
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2) -> HTTP_2
            else -> AUTO
        }
    }
}
