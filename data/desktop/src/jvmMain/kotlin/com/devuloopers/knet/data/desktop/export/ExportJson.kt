package com.devuloopers.knet.data.desktop.export

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.message.*
import kotlinx.serialization.json.*

/** Archive v1 metadata codec. Payload bytes are separate entries, never formatted UI text. */
internal object ExportJson {
    val json = Json { encodeDefaults = true }
    fun obj(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)
    fun headers(values: List<HeaderField>): JsonArray = pairs(values.map { it.name.value to it.value })
    fun pairs(values: List<Pair<String, String>>): JsonArray = buildJsonArray {
        values.forEach { (name, value) -> add(obj { put("name", name); put("value", value) }) }
    }
    fun body(ref: MessageBodyRef): JsonObject = obj {
        when (ref) {
            MessageBodyRef.Empty -> { put("state", "EMPTY"); put("observedBytes", 0); put("storedBytes", 0) }
            is MessageBodyRef.Unavailable -> { put("state", "UNAVAILABLE"); put("captureOutcome", ref.outcome.toString()) }
            is MessageBodyRef.Available -> {
                put("state", if (ref.body.outcome == BodyCaptureOutcome.Complete) "COMPLETE" else "TRUNCATED")
                put("observedBytes", ref.body.observedBytes); put("storedBytes", ref.body.storedBytes)
                put("_bodyId", ref.body.id.value)
                ref.body.digest?.let { put("sha256", it.value) }
                ref.body.contentEncoding?.let { put("contentEncoding", it.token) }
            }
        }
    }
    fun source(ingress: TrafficConnectionIngress?): JsonObject = obj {
        put("kind", ingress?.ingress?.kind?.toString() ?: "Unavailable")
        (TrafficSource.fromIngress(ingress?.ingress) as? TrafficSource.Device)?.let { put("deviceId", it.id.value) }
        ingress?.ingress?.sourceApplicationId?.let { put("applicationId", it) }
    }
    fun exchange(value: HttpExchangeSnapshot, ingress: TrafficConnectionIngress?): JsonObject = obj {
        put("kind", "http"); put("id", value.id.value); put("startedAt", value.startedAtEpochMillis)
        put("state", value.state.name); put("source", source(ingress)); put("origin", value.origin.toString())
        value.connectionId?.let { put("connectionId", it.value) }
        value.streamId?.let { put("streamId", it.value) }
        value.terminalOutcome?.let { put("termination", it.toString()) }
        put("request", obj {
            put("method", value.request.head.method.token); put("url", value.request.absoluteUrl())
            put("protocol", value.request.head.protocol.token); put("headers", headers(value.request.head.headers))
            put("body", body(value.request.body)); put("trailers", headers(value.request.trailers))
        })
        value.response?.let { response -> put("response", obj {
            put("status", response.head.status.code); put("statusText", response.head.reasonPhrase.orEmpty())
            put("protocol", response.head.protocol.token); put("headers", headers(response.head.headers))
            put("body", body(response.body)); put("trailers", headers(response.trailers))
        }) }
        put("timings", obj {
            value.timings.totalMillis?.let { put("total", it) }; value.timings.dnsMillis?.let { put("dns", it) }
            value.timings.connectMillis?.let { put("connect", it) }; value.timings.tlsMillis?.let { put("tls", it) }
            value.timings.firstByteMillis?.let { put("firstByte", it) }; value.timings.downloadMillis?.let { put("download", it) }
            put("connectionReused", value.timings.connectionReused)
        })
        value.appliedNetworkCondition?.let { put("recordedNetworkCondition", it.toString()) }
    }
    fun opaque(value: OpaqueFlowSnapshot, ingress: TrafficConnectionIngress?): JsonObject = obj {
        put("kind", "opaque"); put("id", value.id.value); put("connectionId", value.connectionId.value)
        put("source", source(ingress)); put("host", value.destination.host); put("port", value.destination.port)
        value.serverName?.let { put("serverName", it) }; put("transport", value.transport.name); put("security", value.security.name)
        put("state", value.state.name); put("startedAt", value.startedAtEpochMillis)
        value.completedAtEpochMillis?.let { put("completedAt", it) }
        put("uploadedBytes", value.uploadedBytes); put("downloadedBytes", value.downloadedBytes)
        value.policyAction?.let { put("recordedPolicyAction", it.name) }
        value.policyRuleId?.let { put("recordedPolicyRule", it) }
        value.protectionOrigin?.let { put("recordedPolicyOrigin", it.name) }
        value.inspectionFailureReason?.let { put("inspectionFailure", it.name) }
        value.terminalOutcome?.let { put("termination", it.toString()) }
        value.closure?.let { put("closure", it.toString()) }
        put("offeredAlpn", JsonArray(value.offeredApplicationProtocols.map(::JsonPrimitive)))
        put("offeredTlsVersions", JsonArray(value.offeredTlsVersions.map(::JsonPrimitive)))
        put("payloadAvailability", "ENCRYPTED_OR_NOT_CAPTURED")
    }
    fun message(value: ProtocolMessageSnapshot): JsonObject = obj {
        put("kind", "message"); put("id", value.id.value); put("exchangeId", value.exchangeId.value)
        put("connectionId", value.connectionId.value); put("protocol", value.protocol.value)
        put("messageKind", value.kind.value); put("direction", value.direction.name); put("sequence", value.sequence)
        put("occurredAt", value.occurredAtEpochMillis); put("state", value.state.name)
        put("observedBytes", value.observedBytes); put("compressed", value.compressed)
        value.compressionEncoding?.let { put("compressionEncoding", it) }
        value.terminationReason?.let { put("termination", it.toString()) }
        put("body", body(value.body))
    }
}
