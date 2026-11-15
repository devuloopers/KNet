package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.importcapture.*
import com.devuloopers.knet.application.contract.breakpoint.*
import com.devuloopers.knet.application.contract.networkconditions.*
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.domain.rules.model.*
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.*
import java.net.URI
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Builds editable drafts from bounded imported metadata, never writing live policy or executing requests. */
public class PrepareCaptureImportActionUseCase(
    private val repository: CaptureImportRepository,
    private val breakpoints: BreakpointProtocolRegistry,
    private val conditions: NetworkConditionProtocolRegistry,
) {
    /** Returns an explanation when the selected record cannot faithfully supply this destination. */
    public fun unavailableReason(action: CaptureImportAction, record: ImportedRecord?): String? {
        if (record == null) return "Choose a record from this capture."
        if (endpoint(record) == null) return "This record has no supported destination host and port."
        if (action == CaptureImportAction.NETWORK_CONDITIONS || action == CaptureImportAction.PROTECTED_TRAFFIC) return null
        if (record.request == null || record.method.isNullOrBlank() || httpUri(record.destination) == null)
            return "This record has no captured HTTP request."
        if (action == CaptureImportAction.INTERCEPT) return null
        val body = record.request.body
        if (body.state !in setOf(ImportedBodyState.COMPLETE, ImportedBodyState.EMPTY))
            return "The request body is ${body.state.name.lowercase()}; a complete request is required."
        if (body.bytes > MAXIMUM_DRAFT_BYTES) return "The request body exceeds the 1 MiB draft limit. Choose another record."
        val type = record.request.headers.firstOrNull { it.first.equals("Content-Type", true) }?.second.orEmpty().lowercase()
        if (body.bytes > 0 && !body.utf8Text && !(type.startsWith("text/") || type.contains("json") || type.contains("xml") || type.contains("graphql") || type.contains("x-www-form-urlencoded")))
            return "This action requires a complete text request."
        if (!body.decoded && record.request.headers.any { it.first.equals("Content-Encoding", true) && !it.second.equals("identity", true) })
            return "The captured request is still compressed. Choose another record."
        if (record.request.headers.any { it.first.equals("Upgrade", true) && it.second.equals("websocket", true) })
            return "This upgrade does not contain a complete standalone HTTP request. Choose another record."
        return null
    }

    /** Loads at most a bounded request body and prepares one action under [workspace], supplied by the UI owner. */
    @OptIn(ExperimentalUuidApi::class)
    public suspend fun execute(capture: ImportedCapture, index: Long?, action: CaptureImportAction, workspace: PolicyWorkspace): CaptureImportActionDraft {
        val record = repository.record(capture.id, requireNotNull(index) { "Choose a record first." })
        require(unavailableReason(action, record) == null) { unavailableReason(action, record).orEmpty() }
        val target = requireNotNull(endpoint(record))
        val uri = httpUri(record.destination)
        val message = record.request
        var bytes = byteArrayOf()
        var complete = message?.body?.state == ImportedBodyState.EMPTY
        if (action != CaptureImportAction.PROTECTED_TRAFFIC && message != null && message.body.state == ImportedBodyState.COMPLETE) {
            val preview = repository.body(capture.id, message.body, MAXIMUM_DRAFT_BYTES)
            bytes = preview.bytes
            complete = !preview.truncated && bytes.size.toLong() == message.body.bytes
        }
        val request = if (message != null && uri != null && record.method != null) HttpRequestSnapshot(RequestHead(
            HttpMethod.fromToken(record.method), RequestTarget.Absolute(HttpScheme.fromToken(uri.scheme.lowercase()),
                Authority(target.normalizedHost, target.port), (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")),
            ApplicationProtocol.fromToken("HTTP/1.1"), message.headers.map { HeaderField(HeaderName(it.first), it.second) })) else null
        return when (action) {
            CaptureImportAction.PROTECTED_TRAFFIC -> CaptureImportActionDraft.ProtectedTraffic(target, workspace)
            CaptureImportAction.API_STUDIO -> {
                require(complete) { "The complete request body is unavailable. Choose another record." }
                val text = bytes.decodeToString(throwOnInvalidSequence = true)
                val headers = requireNotNull(message).headers.filterNot { message.body.decoded && it.first.equals("Content-Encoding", true) }
                val type = headers.firstOrNull { it.first.equals("Content-Type", true) }?.second.orEmpty().lowercase()
                val bodyType = when {
                    bytes.isEmpty() -> RequestBodyType.NONE
                    type.contains("json") -> RequestBodyType.JSON
                    type.contains("xml") -> RequestBodyType.XML
                    type.contains("graphql") -> RequestBodyType.GRAPHQL
                    type.contains("x-www-form-urlencoded") -> RequestBodyType.X_WWW_FORM_URLENCODED
                    else -> RequestBodyType.RAW_TEXT
                }
                CaptureImportActionDraft.ApiStudio(NetworkRequestSpec(method = requireNotNull(request).head.method,
                    url = record.destination.substringBefore('#'), headers = headers, bodyPayload = text, bodyType = bodyType,
                    timestamp = record.startedAt ?: 0))
            }
            CaptureImportAction.INTERCEPT -> {
                val criteria = breakpoints.suggestCriteria(BreakpointRuleSuggestionInput(requireNotNull(request),
                    bytes.takeIf { it.isNotEmpty() }?.let(::BreakpointBody), complete)) ?: ProtocolMatchCriteria.HttpDefault
                val pattern = "${uri!!.scheme}://${if (target.normalizedHost.contains(':')) "[${target.normalizedHost}]" else target.normalizedHost}${uri.rawPath?.ifEmpty { "/" } ?: "/"}"
                CaptureImportActionDraft.Intercept(BreakpointRule(id = Uuid.random().toString(), name = pattern, urlPattern = pattern,
                    portCriteria = target.port?.let(BreakpointPortCriteria::Exact) ?: BreakpointPortCriteria.Any,
                    method = request.head.method, phase = BreakpointPhase.BOTH, enabled = true, protocolCriteria = criteria),
                    breakpoints.editorValues(criteria), workspace)
            }
            CaptureImportAction.NETWORK_CONDITIONS -> {
                val criteria = request?.let { conditions.suggestCriteria(NetworkConditionHttpInspectionInput(it,
                    bytes.takeIf { value -> value.isNotEmpty() }?.let(::NetworkConditionBody), complete)) }
                    ?: NetworkConditionProtocolCriteria.TransportDefault
                CaptureImportActionDraft.NetworkConditions(target, criteria, conditions.editorValues(criteria), workspace)
            }
        }
    }

    private fun httpUri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() && it.port in -1..65_535 && it.port != 0
    }

    private fun endpoint(record: ImportedRecord): NetworkConditionTarget? = runCatching {
        val absolute = httpUri(record.destination)
        if (absolute != null) NetworkConditionTarget.parse(absolute.host.removeSurrounding("[", "]"),
            absolute.port.takeIf { it > 0 } ?: if (absolute.scheme.equals("https", true)) 443 else 80)
        else if (record.request == null && !record.destination.contains('/') && !record.destination.any(Char::isWhitespace)) {
            val authority = URI("https://${record.destination}")
            if (authority.host.isNullOrBlank() || authority.port !in 1..65_535 || authority.rawUserInfo != null) null
            else NetworkConditionTarget.parse(authority.host.removeSurrounding("[", "]"), authority.port)
        } else null
    }.getOrNull()

    private companion object { const val MAXIMUM_DRAFT_BYTES = 1_048_576 }
}
