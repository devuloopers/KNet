package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.contract.traffic.TrafficArrivalCheckpoint
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings

/**
 * Small per-source reading state, separate from the bounded row-window cache.
 * @property atTop Whether the actual newest matching record is fully visible at the live edge.
 * @property acknowledged Last insertion boundary shown at the live edge or explicitly acknowledged.
 * @property observed Last sampled boundary, used to freeze paging while reading history.
 * @property windowThrough Frozen window boundary, captured before the next arrival refresh.
 * @property newCount Matching arrivals remaining above the last-seen checkpoint, independent of lifecycle updates.
 * @property seenThrough Latest progressively viewed position and its known insertion snapshot.
 * @property latest Actual newest matching stored row, independent of the currently loaded window.
 * @property revision Read-progress revision used to fence stale count queries.
 * @property jumpRevision Explicit one-time jump command; Manual's switch remains unchanged.
 */
data class TrafficReadingState(
    val atTop: Boolean = true,
    val acknowledged: TrafficArrivalCheckpoint? = null,
    val observed: TrafficArrivalCheckpoint? = null,
    val windowThrough: TrafficArrivalCheckpoint? = null,
    val newCount: Long = 0,
    val jumpRevision: Long = 0,
    val seenThrough: com.devuloopers.knet.application.contract.traffic.TrafficSeenCheckpoint? = null,
    val latest: com.devuloopers.knet.application.contract.traffic.TrafficReadPoint? = null,
    val revision: Long = 0,
)

/** True only at the exact live edge; a partially clipped newest row pauses Smart mode. */
internal fun isTrafficAtTop(row: Int, offset: Int): Boolean = row == 0 && offset == 0

/** Resolves the persisted strategy without deriving user intent from a data-driven list relocation. */
internal fun TrafficAutoScrollSettings.follows(reading: TrafficReadingState): Boolean = when (mode) {
    TrafficAutoScrollMode.SMART -> reading.atTop
    TrafficAutoScrollMode.MANUAL -> manualEnabled
}

/**
 * One measured list viewport, containing only fully visible stable row keys.
 * @property transactionIds Body-free canonical row keys; partially clipped and offscreen rows are excluded.
 * @property atWindowTop Whether the loaded first row is fully visible at offset zero.
 */
data class TrafficVisibleRows(val transactionIds: List<String>, val atWindowTop: Boolean)

/**
 * Returns a forward-only last-seen update from fully visible records, or null when nothing new was viewed.
 * A late-admitted/backdated visible record can advance the known insertion snapshot without regressing
 * the chronological position. Merely repeating the old visible rows does not acknowledge that admission.
 */
internal fun TrafficReadingState.nextSeenCheckpoint(
    visible: List<com.devuloopers.knet.application.contract.traffic.TrafficReadPoint>,
): com.devuloopers.knet.application.contract.traffic.TrafficSeenCheckpoint? {
    val baseline = acknowledged ?: return null
    val snapshot = observed ?: return null
    val eligible = visible.filter { it.isAfter(baseline) }
    val newest = eligible.maxOrNull() ?: return null
    val previous = seenThrough
    if (previous != null && newest <= previous.position && eligible.none { it.isAfter(previous.observedAt) }) return null
    return com.devuloopers.knet.application.contract.traffic.TrafficSeenCheckpoint(
        if (previous == null) newest else maxOf(newest, previous.position), snapshot)
}
