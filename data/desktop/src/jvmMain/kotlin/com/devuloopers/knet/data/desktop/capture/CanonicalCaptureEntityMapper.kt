package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.BodyStorageKey
import com.devuloopers.knet.storage.capture.entity.BodyObjectEntity
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.CaptureEvent
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyDigest
import com.devuloopers.knet.traffic.model.body.BodyDigestAlgorithm
import com.devuloopers.knet.traffic.model.body.BodyFailure
import com.devuloopers.knet.traffic.model.body.BodyRef
import com.devuloopers.knet.traffic.model.body.BodySkipReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead

/** Maps portable canonical capture values into Room-owned entities and encodings. */
internal object CanonicalCaptureEntityMapper {
    /** Maps a connection-open event to its initial durable row. */
    fun connection(event: CaptureEvent.ConnectionOpened): TrafficConnectionEntity = TrafficConnectionEntity(
        id = event.connectionId.value,
        sessionId = event.sessionId.value,
        sequenceVersion = event.sequence,
        openedAtEpochMillis = event.occurredAtEpochMillis,
        closedAtEpochMillis = null,
        ingressKind = ingressToken(event.ingress.kind),
        clientIdentity = event.ingress.clientIdentity?.value,
        downstreamHost = event.downstream?.host,
        downstreamPort = event.downstream?.port,
        listenerHost = event.localListener.host,
        listenerPort = event.localListener.port,
        transportProtocol = event.transportProtocol,
        receivedBytes = 0L,
        sentBytes = 0L,
        state = "OPEN",
        terminalErrorCode = null,
    )

    /** Maps an exchange-start event to an immutable request row. */
    fun exchange(event: CaptureEvent.ExchangeStarted): CanonicalExchangeEntity {
        val target = targetColumns(event.request.target)
        return CanonicalExchangeEntity(
            id = event.exchangeId.value,
            sessionId = event.sessionId.value,
            connectionId = event.connectionId.value,
            streamId = event.streamId?.value,
            connectionSequence = event.sequence,
            version = event.exchangeVersion,
            state = "REQUEST_HEADERS",
            startedAtEpochMillis = event.occurredAtEpochMillis,
            completedAtEpochMillis = null,
            method = event.request.method.token,
            scheme = target.scheme,
            host = target.host,
            port = target.port,
            pathAndQuery = target.pathAndQuery,
            protocol = event.request.protocol.token,
            requestHeadersEncoded = encodeHeaders(event.request.headers),
            requestBodyId = null,
            responseProtocol = null,
            responseStatusCode = null,
            responseReasonPhrase = null,
            responseHeadersEncoded = null,
            responseBodyId = null,
            timingDnsMillis = null,
            timingConnectMillis = null,
            timingTlsMillis = null,
            timingFirstByteMillis = null,
            timingDownloadMillis = null,
            timingTotalMillis = null,
            terminalErrorCode = null,
        )
    }

    /** Maps finalized body metadata to its immutable body-object row. */
    fun body(event: CaptureEvent.BodyCaptured, storageKey: BodyStorageKey): BodyObjectEntity = BodyObjectEntity(
        id = event.body.id.value,
        sessionId = event.sessionId.value,
        exchangeId = event.exchangeId.value,
        direction = event.direction.name,
        observedBytes = event.body.observedBytes,
        storedBytes = event.body.storedBytes,
        digestAlgorithm = event.body.digest?.algorithm?.name,
        digestValue = event.body.digest?.value,
        contentEncoding = event.body.contentEncoding?.token,
        outcome = outcomeToken(event.body.outcome),
        state = "FINALIZED",
        createdAtEpochMillis = event.occurredAtEpochMillis,
        finalizedAtEpochMillis = event.occurredAtEpochMillis,
        storageKey = storageKey.value,
    )

    /** Encodes ordered duplicate-preserving headers using a versioned length-prefix format. */
    fun encodeHeaders(headers: List<HeaderField>): String = buildString {
        append("H1:")
        append(headers.size)
        append(':')
        headers.forEach { header ->
            append(header.name.value.length)
            append(':')
            append(header.name.value)
            append(header.value.length)
            append(':')
            append(header.value)
        }
    }

    /** Maps a canonical exchange row and its page-loaded bodies to the shared feature snapshot. */
    fun snapshot(
        exchange: CanonicalExchangeEntity,
        bodies: Map<String, BodyObjectEntity>,
    ): HttpExchangeSnapshot {
        val request = HttpRequestSnapshot(
            head = RequestHead(
                method = HttpMethod.fromToken(exchange.method),
                target = requestTarget(exchange),
                protocol = ApplicationProtocol.fromToken(exchange.protocol),
                headers = decodeHeaders(exchange.requestHeadersEncoded),
            ),
            body = messageBody(exchange.requestBodyId, bodies),
        )
        val response = exchange.responseStatusCode?.let { statusCode ->
            HttpResponseSnapshot(
                head = ResponseHead(
                    protocol = ApplicationProtocol.fromToken(exchange.responseProtocol ?: exchange.protocol),
                    status = HttpStatus(statusCode),
                    reasonPhrase = exchange.responseReasonPhrase,
                    headers = decodeHeaders(exchange.responseHeadersEncoded ?: EMPTY_HEADERS),
                ),
                body = messageBody(exchange.responseBodyId, bodies),
            )
        }
        return HttpExchangeSnapshot(
            id = ExchangeId(exchange.id),
            connectionId = ConnectionId(exchange.connectionId),
            streamId = exchange.streamId?.let(::StreamId),
            request = request,
            response = response,
            state = runCatching { ExchangeState.valueOf(exchange.state) }.getOrDefault(ExchangeState.FAILED),
            timings = ExchangeTimings(
                dnsMillis = exchange.timingDnsMillis,
                connectMillis = exchange.timingConnectMillis,
                tlsMillis = exchange.timingTlsMillis,
                firstByteMillis = exchange.timingFirstByteMillis,
                downloadMillis = exchange.timingDownloadMillis,
                totalMillis = exchange.timingTotalMillis,
            ),
            startedAtEpochMillis = exchange.startedAtEpochMillis,
        )
    }

    /** Decodes the versioned length-prefix header format while preserving order and duplicates. */
    fun decodeHeaders(encoded: String): List<HeaderField> {
        require(encoded.startsWith(HEADER_PREFIX)) { "Unsupported canonical header encoding." }
        var index = HEADER_PREFIX.length
        fun readLength(): Int {
            val delimiter = encoded.indexOf(':', index)
            require(delimiter >= index) { "Malformed canonical header encoding." }
            val value = encoded.substring(index, delimiter).toInt()
            require(value >= 0) { "Canonical header length must not be negative." }
            index = delimiter + 1
            return value
        }
        val count = readLength()
        val headers = ArrayList<HeaderField>(count)
        repeat(count) {
            val nameLength = readLength()
            require(index + nameLength <= encoded.length) { "Canonical header name exceeds its encoding." }
            val name = encoded.substring(index, index + nameLength)
            index += nameLength
            val valueLength = readLength()
            require(index + valueLength <= encoded.length) { "Canonical header value exceeds its encoding." }
            val value = encoded.substring(index, index + valueLength)
            index += valueLength
            headers += HeaderField(HeaderName(name), value)
        }
        require(index == encoded.length) { "Canonical header encoding has trailing data." }
        return headers
    }

    /** Converts a typed body outcome to a stable persistence token. */
    private fun outcomeToken(outcome: BodyCaptureOutcome): String = when (outcome) {
        BodyCaptureOutcome.Complete -> "COMPLETE"
        is BodyCaptureOutcome.Truncated -> "TRUNCATED:${outcome.limitBytes}"
        is BodyCaptureOutcome.Skipped -> "SKIPPED:${outcome.reason.name}"
        is BodyCaptureOutcome.Failed -> "FAILED:${failureToken(outcome.reason)}"
    }

    /** Converts a typed body failure to a stable persistence token. */
    private fun failureToken(failure: BodyFailure): String = when (failure) {
        BodyFailure.StorageFull -> "STORAGE_FULL"
        BodyFailure.PermissionDenied -> "PERMISSION_DENIED"
        BodyFailure.SourceFailed -> "SOURCE_FAILED"
        is BodyFailure.Custom -> failure.code
    }

    /** Reconstructs a canonical body relationship from page-loaded metadata. */
    private fun messageBody(
        bodyId: String?,
        bodies: Map<String, BodyObjectEntity>,
    ): MessageBodyRef {
        if (bodyId == null) return MessageBodyRef.Empty
        val entity = bodies[bodyId] ?: return MessageBodyRef.Unavailable(
            BodyCaptureOutcome.Failed(BodyFailure.Custom("body-metadata-missing"))
        )
        val outcome = parseOutcome(entity.outcome)
        if (entity.state != BODY_STATE_FINALIZED) return MessageBodyRef.Unavailable(outcome)
        return MessageBodyRef.Available(
            BodyRef(
                id = BodyId(entity.id),
                observedBytes = entity.observedBytes,
                storedBytes = entity.storedBytes,
                digest = entity.digestValue?.let { value ->
                    BodyDigest(
                        algorithm = runCatching {
                            BodyDigestAlgorithm.valueOf(entity.digestAlgorithm ?: BodyDigestAlgorithm.SHA_256.name)
                        }.getOrDefault(BodyDigestAlgorithm.SHA_256),
                        value = value,
                    )
                },
                contentEncoding = entity.contentEncoding?.let(ContentEncoding::fromToken),
                outcome = outcome,
            )
        )
    }

    /** Parses stable body outcome tokens written by [outcomeToken]. */
    private fun parseOutcome(token: String): BodyCaptureOutcome = when {
        token == "COMPLETE" -> BodyCaptureOutcome.Complete
        token.startsWith("TRUNCATED:") -> BodyCaptureOutcome.Truncated(
            token.substringAfter(':').toLong(),
        )
        token.startsWith("SKIPPED:") -> BodyCaptureOutcome.Skipped(
            BodySkipReason.valueOf(token.substringAfter(':')),
        )
        token == "FAILED:STORAGE_FULL" -> BodyCaptureOutcome.Failed(BodyFailure.StorageFull)
        token == "FAILED:PERMISSION_DENIED" -> BodyCaptureOutcome.Failed(BodyFailure.PermissionDenied)
        token == "FAILED:SOURCE_FAILED" -> BodyCaptureOutcome.Failed(BodyFailure.SourceFailed)
        token.startsWith("FAILED:") -> BodyCaptureOutcome.Failed(BodyFailure.Custom(token.substringAfter(':')))
        else -> BodyCaptureOutcome.Failed(BodyFailure.Custom("body-outcome-invalid"))
    }

    /** Reconstructs the typed request target stored in indexed columns. */
    private fun requestTarget(exchange: CanonicalExchangeEntity): RequestTarget {
        val scheme = exchange.scheme
        val host = exchange.host
        return when {
        scheme != null && host != null -> RequestTarget.Absolute(
            scheme = HttpScheme.fromToken(scheme),
            authority = Authority(host, exchange.port),
            pathAndQuery = exchange.pathAndQuery,
        )
        host != null -> RequestTarget.AuthorityForm(Authority(host, exchange.port))
        exchange.pathAndQuery == "*" -> RequestTarget.Asterisk
        exchange.pathAndQuery.startsWith('/') -> RequestTarget.Origin(exchange.pathAndQuery)
        else -> RequestTarget.Custom(exchange.pathAndQuery)
        }
    }

    /** Converts ingress variants to stable storage tokens without exposing implementation types. */
    private fun ingressToken(kind: IngressKind): String = when (kind) {
        IngressKind.Local -> "LOCAL"
        IngressKind.LanPairedDevice -> "LAN_PAIRED_DEVICE"
        IngressKind.WifiApprovedDevice -> "WIFI_APPROVED_DEVICE"
        IngressKind.AdbDevice -> "ADB_DEVICE"
        IngressKind.CompanionDirect -> "COMPANION_DIRECT"
        IngressKind.CompanionRelay -> "COMPANION_RELAY"
        is IngressKind.Custom -> "CUSTOM:${kind.value}"
    }

    /** Extracts indexed target columns without JVM URI parsing. */
    private fun targetColumns(target: RequestTarget): TargetColumns = when (target) {
        is RequestTarget.Absolute -> TargetColumns(
            scheme = target.scheme.token,
            host = target.authority.host,
            port = target.authority.port,
            pathAndQuery = target.pathAndQuery,
        )
        is RequestTarget.Origin -> TargetColumns(null, null, null, target.pathAndQuery)
        is RequestTarget.AuthorityForm -> TargetColumns(
            scheme = null,
            host = target.authority.host,
            port = target.authority.port,
            pathAndQuery = target.authority.host,
        )
        RequestTarget.Asterisk -> TargetColumns(null, null, null, "*")
        is RequestTarget.Custom -> TargetColumns(null, null, null, target.value)
    }

    /** Indexed request-target columns used by [exchange]. */
    private data class TargetColumns(
        val scheme: String?,
        val host: String?,
        val port: Int?,
        val pathAndQuery: String,
    )

    private const val HEADER_PREFIX = "H1:"
    private const val EMPTY_HEADERS = "H1:0:"
    private const val BODY_STATE_FINALIZED = "FINALIZED"
}
