package com.devuloopers.knet.engine.session.export

import com.devuloopers.knet.domain.network.model.HttpTransaction

/**
 * Utility generating executable cURL command line strings from HTTP transactions.
 */
object CurlGenerator {

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
