package com.devuloopers.knet.ui.desktop.apistudio.grpc.model

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOperation

data class GrpcStudioState(
    val documentId: String,
    val targetHost: String = "",
    val targetPort: String = "",
    val useTls: Boolean = false,
    val deadlineMillis: String = "",
    val schemaSourceId: String? = null,
    val operations: List<ApiStudioProtocolOperation> = emptyList(),
    val selectedOperation: ApiStudioProtocolOperation? = null,
    val metadata: List<ApiStudioProtocolMetadataEntry> = emptyList(),
    val outboundMessages: List<String> = listOf(""),
    val selectedOutboundIndex: Int = 0,
    val events: List<ApiStudioProtocolExecutionEvent> = emptyList(),
    val selectedEventIndex: Int? = null,
    val isExecuting: Boolean = false,
    val isInteractiveSession: Boolean = false,
    val isRequestHalfClosed: Boolean = false,
    val isReflecting: Boolean = false,
    val isDirty: Boolean = true,
    val errorMessage: String? = null,
) {
    val selectedOutboundMessage: String
        get() = outboundMessages.getOrElse(selectedOutboundIndex) { "" }

    /** Omits untouched editor rows while preserving partially authored rows for validation. */
    val authoredMetadata: List<ApiStudioProtocolMetadataEntry>
        get() = metadata.filterNot { entry -> entry.name.isBlank() && entry.value.isBlank() }

    /** Basic presentation eligibility for operations that connect to a target. */
    val hasValidTarget: Boolean
        get() {
            val port = targetPort.toIntOrNull()
            return targetHost.isNotBlank() && port != null && port in 1..65_535
        }

    /** Basic presentation eligibility; protocol authoring still performs authoritative validation. */
    val canInvoke: Boolean
        get() {
            val deadline = deadlineMillis.toLongOrNull()
            return selectedOperation != null &&
                hasValidTarget &&
                outboundMessages.all { it.isNotBlank() } &&
                (deadlineMillis.isBlank() || deadline != null && deadline in 1L..3_600_000L)
        }
}
