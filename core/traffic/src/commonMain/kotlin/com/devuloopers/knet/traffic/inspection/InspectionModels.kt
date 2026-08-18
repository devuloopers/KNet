package com.devuloopers.knet.traffic.inspection

import com.devuloopers.knet.traffic.id.ExchangeId

/** Stable semantic-inspector identifier. */

@JvmInline
public value class InspectorId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Inspector ID must not be blank." }
    }
}

/** Terminal state of one versioned semantic inspection attempt. */
public enum class InspectionAnnotationState {
    COMPLETED,
    FAILED,
    SKIPPED,
}

/** Generic field rendered without importing a protocol implementation into Traffic UI. */
public data class InspectionField(
    public val label: String,
    public val value: String,
) {
    init {
        require(label.isNotBlank()) { "Inspection field label must not be blank." }
    }
}

/** Versioned protocol-neutral document produced by a semantic inspector. */
public data class InspectionDocument(
    public val kind: String,
    public val title: String,
    public val summary: String? = null,
    public val fields: List<InspectionField> = emptyList(),
) {
    init {
        require(kind.isNotBlank()) { "Inspection document kind must not be blank." }
        require(title.isNotBlank()) { "Inspection document title must not be blank." }
        require(fields.size <= 256) { "Inspection document exceeds the field limit." }
    }
}

/** Durable, rerunnable annotation attached to one canonical exchange. */
public data class InspectionAnnotation(
    public val exchangeId: ExchangeId,
    public val inspectorId: InspectorId,
    public val schemaVersion: Long,
    public val state: InspectionAnnotationState,
    public val document: InspectionDocument? = null,
    public val errorCode: String? = null,
    public val createdAtEpochMillis: Long,
) {
    init {
        require(schemaVersion > 0L) { "Inspection schema version must be positive." }
        require(createdAtEpochMillis >= 0L) { "Inspection timestamp must not be negative." }
        require(errorCode == null || errorCode.isNotBlank()) { "Inspection error code must be null or non-blank." }
        require(state != InspectionAnnotationState.COMPLETED || document != null) {
            "A completed inspection requires a document."
        }
    }
}
