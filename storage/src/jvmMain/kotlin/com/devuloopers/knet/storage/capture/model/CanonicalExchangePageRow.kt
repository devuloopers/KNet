package com.devuloopers.knet.storage.capture.model

import androidx.room.Embedded
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity

/** Storage page projection that separates durable identity from the retained-history ordinal. */
data class CanonicalExchangePageRow(
    @Embedded val exchange: CanonicalExchangeEntity,
    val historySequence: Long,
)
