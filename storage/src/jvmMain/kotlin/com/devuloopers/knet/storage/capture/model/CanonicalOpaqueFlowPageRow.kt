package com.devuloopers.knet.storage.capture.model

import androidx.room.Embedded
import com.devuloopers.knet.storage.capture.entity.OpaqueFlowEntity

/**
 * One payload-opaque flow plus its current retained-flow ordinal.
 *
 * @property flow Durable flow metadata.
 * @property historySequence One-based ordinal among retained opaque flows.
 */
data class CanonicalOpaqueFlowPageRow(
    @Embedded val flow: OpaqueFlowEntity,
    val historySequence: Long,
)
