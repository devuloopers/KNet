package com.devuloopers.knet.application.contract.apistudio

import com.devuloopers.knet.domain.request.descriptor.RequestKindId

/** Opaque imported schema owned by one API Studio protocol extension. */
public class ApiStudioProtocolSchemaSource(
    public val kind: RequestKindId,
    public val sourceId: String,
    payload: ByteArray,
) {
    private val encodedPayload: ByteArray = payload.copyOf()

    init {
        require(sourceId.isNotBlank()) { "Protocol schema source ID must not be blank." }
        require(encodedPayload.isNotEmpty()) { "Protocol schema source must not be empty." }
        require(encodedPayload.size <= MAXIMUM_SCHEMA_BYTES) { "Protocol schema source is too large." }
    }

    public fun copyPayload(): ByteArray = encodedPayload.copyOf()

    public companion object {
        public const val MAXIMUM_SCHEMA_BYTES: Int = 16 * 1_024 * 1_024
    }
}
