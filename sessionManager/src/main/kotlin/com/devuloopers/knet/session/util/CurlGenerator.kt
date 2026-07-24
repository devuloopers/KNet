package com.devuloopers.knet.session.util

import com.devuloopers.knet.model.HttpTransaction

/**
 * Utility responsible for generating executable cURL syntax commands from HTTP transaction records.
 */
object CurlGenerator {

    /**
     * Constructs a standard executable cURL command string from an [HttpTransaction].
     *
     * @param transaction The HTTP transaction record.
     * @return Fully formatted cURL terminal command.
     */
    fun generate(transaction: HttpTransaction): String {
        val req = transaction.request
        val sb = StringBuilder("curl -X ${req.method} \"${req.url}\"")
        req.headers.forEach { (name, value) ->
            sb.append(" -H \"$name: ${value.replace("\"", "\\\"")}\"")
        }
        val body = req.body
        if (body != null && body.isNotEmpty()) {
            val escapedBody = String(body).replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            sb.append(" -d \"$escapedBody\"")
        }
        return sb.toString()
    }
}
