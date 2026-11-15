package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.contract.traffic.TrafficRecordId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.OpaqueFlowId

/** Platform-neutral mouse selection intent; Meta/Ctrl share additive selection semantics. */
enum class TrafficSelectionGesture {
    REPLACE, TOGGLE, RANGE, EXTEND_RANGE,
    /** Focuses a context-menu target, preserving a selected group or replacing unrelated selection. */
    CONTEXT,
}

/** Body-free selection retained by its original Traffic source, independently of the loaded row window. */
data class TrafficRowSelection(
    /** Exact canonical identities; arrivals and page eviction do not expand or erase them. */
    val records: Set<TrafficRecordId> = emptySet(),
    /** Stable range anchor; an evicted anchor falls back to the clicked row. */
    val anchor: TrafficRecordId? = null,
    /** True after initial selection or an explicit gesture, including intentionally selecting nothing. */
    val initialized: Boolean = records.isNotEmpty(),
) {
    /** Selects the first focused row once; empty pages wait, and later projections preserve user choices. */
    fun initialize(id: TrafficRecordId?): TrafficRowSelection =
        if (initialized || id == null) this else TrafficRowSelection(setOf(id), id)

    /** Explicitly deselects all rows without allowing a later projection to reselect the focused row. */
    fun clear(): TrafficRowSelection = TrafficRowSelection(initialized = true)

    /** Applies an explicit gesture against the currently rendered ordering, or rejects a capacity overflow. */
    fun select(id: TrafficRecordId, gesture: TrafficSelectionGesture, loaded: List<TrafficRecordId>): TrafficRowSelection? {
        if (id !in loaded) return this
        val index = loaded.indexOf(id)
        val retainedAnchorIndex = anchor?.let(loaded::indexOf)?.takeIf { it >= 0 }
        val anchorIndex = retainedAnchorIndex ?: index
        val range = loaded.subList(minOf(index, anchorIndex), maxOf(index, anchorIndex) + 1).toSet()
        val next = when (gesture) {
            TrafficSelectionGesture.REPLACE -> setOf(id)
            TrafficSelectionGesture.TOGGLE -> if (id in records) records - id else records + id
            TrafficSelectionGesture.RANGE -> range
            TrafficSelectionGesture.EXTEND_RANGE -> records + range
            TrafficSelectionGesture.CONTEXT -> if (id in records) records else setOf(id)
        }
        if (next.size > MAXIMUM_ITEMS) return null
        return copy(records = next, anchor = if (gesture == TrafficSelectionGesture.CONTEXT && id in records) anchor
            else if (gesture == TrafficSelectionGesture.RANGE || gesture == TrafficSelectionGesture.EXTEND_RANGE)
            anchor?.takeIf { retainedAnchorIndex != null } ?: id else id, initialized = true)
    }

    /** Explicit selection bounds; larger complete result sets use the existing filtered/source exporter. */
    companion object { const val MAXIMUM_ITEMS: Int = 10_000 }
}

/** Converts presentation identity into a typed canonical key without conflating HTTP and opaque tables. */
val TrafficRowUiState.recordId: TrafficRecordId
    get() = if (rowKind == TrafficRowKind.OPAQUE_FLOW) TrafficRecordId.Opaque(OpaqueFlowId(transactionId))
    else TrafficRecordId.Exchange(ExchangeId(transactionId))

/** Selection interactions; callbacks contain no storage or platform dependencies. */
data class TrafficSelectionActions(
    /** Selects/focuses one row using the captured modifier gesture. */
    val select: (TrafficRecordId, TrafficSelectionGesture) -> Unit,
    /** Selects the loaded rows, explicitly named to avoid claiming an unbounded query selection. */
    val selectLoaded: () -> Unit,
    /** Clears selection without changing inspector focus. */
    val clear: () -> Unit,
    /** Freezes and exports the current exact selection. */
    val export: () -> Unit,
)
