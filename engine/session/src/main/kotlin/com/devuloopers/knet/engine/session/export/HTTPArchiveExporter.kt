package com.devuloopers.knet.engine.session.export

import com.devuloopers.knet.domain.network.model.HttpTransaction
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/**
 * Exporter utility serializing transaction records into W3C standard HAR (HTTP Archive) 1.2 JSON structures.
 */
object HTTPArchiveExporter {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun export(transactions: List<HttpTransaction>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val entries = transactions.map { httpTransaction ->
            val startedDateTime = dateFormat.format(Date(httpTransaction.timestamp))
            val req = httpTransaction.request
            val res = httpTransaction.response

            val reqCookies = parseRequestCookies(req.headers).map { HarCookie(it.first, it.second) }
            val reqQueryParams = parseQueryString(req.url).map { HarQueryParam(it.first, it.second) }

            val reqBody = req.body
            val reqBodyText = reqBody?.let { String(it) } ?: ""
            val reqMimeType = req.headers
                .firstOrNull {
                    it.first.equals("content-type", ignoreCase = true)
                }?.second ?: "application/octet-stream"

            val postData = if (reqBody != null && reqBody.isNotEmpty()) {
                HarPostData(mimeType = reqMimeType, text = reqBodyText)
            } else {
                null
            }

            val request = HarRequest(
                method = req.method,
                url = req.url,
                headers = req.headers.map { HarHeader(it.first, it.second) },
                queryString = reqQueryParams,
                cookies = reqCookies,
                bodySize = reqBody?.size ?: 0,
                postData = postData
            )

            val response = if (res != null) {
                val resCookies = parseResponseCookies(res.headers).map { HarCookie(it.first, it.second) }
                val resBodyText = res.body?.let { String(it) } ?: ""
                val resMimeType = res.headers
                    .firstOrNull {
                        it.first.equals("content-type", ignoreCase = true)
                    }?.second ?: "application/octet-stream"

                HarResponse(
                    status = res.statusCode,
                    statusText = res.statusText,
                    headers = res.headers.map { HarHeader(it.first, it.second) },
                    cookies = resCookies,
                    content = HarContent(
                        size = res.body?.size ?: 0,
                        mimeType = resMimeType,
                        text = resBodyText
                    ),
                    bodySize = res.body?.size ?: -1
                )
            } else {
                null
            }

            HarEntry(
                startedDateTime = startedDateTime,
                time = httpTransaction.durationMs.toDouble(),
                request = request,
                response = response,
                timings = HarTimings(wait = httpTransaction.durationMs)
            )
        }

        val harRoot = HarLogRoot(
            log = HarLog(
                entries = entries
            )
        )

        return json.encodeToString(harRoot)
    }

    private fun parseQueryString(url: String): List<Pair<String, String>> {
        val index = url.indexOf('?')
        if (index == -1 || index == url.length - 1) return emptyList()
        val query = url.substring(index + 1)
        return query.split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.isEmpty()) null
            else if (parts.size == 1) Pair(parts[0], "")
            else Pair(parts[0], parts[1])
        }
    }

    private fun parseRequestCookies(headers: List<Pair<String, String>>): List<Pair<String, String>> {
        val cookieHeader =
            headers.firstOrNull { it.first.equals("cookie", ignoreCase = true) }?.second ?: return emptyList()
        return cookieHeader.split(';').mapNotNull { cookie ->
            val parts = cookie.split('=', limit = 2)
            if (parts.size == 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                null
            }
        }
    }

    private fun parseResponseCookies(headers: List<Pair<String, String>>): List<Pair<String, String>> {
        val setCookieHeaders = headers.filter { it.first.equals("set-cookie", ignoreCase = true) }
        return setCookieHeaders.mapNotNull { (_, value) ->
            val mainPart = value.split(';').firstOrNull() ?: return@mapNotNull null
            val parts = mainPart.split('=', limit = 2)
            if (parts.size == 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                null
            }
        }
    }
}
