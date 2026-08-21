package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths

/**
 * Inclusive resize limits for one Traffic table column.
 *
 * @property minimumDp Smallest usable width that preserves the column identity.
 * @property maximumDp Largest supported width before the table becomes needlessly sparse.
 */
internal data class TrafficColumnWidthLimits(
    val minimumDp: Float,
    val maximumDp: Float,
)

/**
 * Fully resolved visible Traffic table layout for one measured viewport.
 *
 * @property widthsDp Width of every visible column, keyed by typed column identity.
 * @property tableWidthDp Total row width including the table's horizontal content padding.
 */
internal data class ResolvedTrafficColumnLayout(
    val widthsDp: Map<TrafficColumn, Float>,
    val tableWidthDp: Float,
) {
    /**
     * Returns the resolved width for [column].
     *
     * @throws IllegalArgumentException When a caller requests a hidden or unresolved column.
     */
    fun widthDp(column: TrafficColumn): Float = requireNotNull(widthsDp[column]) {
        "Traffic column ${column.name} is not present in the resolved layout."
    }
}

/** Returns a copy with [column] constrained to its supported resize range. */
internal fun TrafficTableColumnWidths.withColumnWidth(
    column: TrafficColumn,
    requestedWidthDp: Float,
): TrafficTableColumnWidths {
    val width = requestedWidthDp.coerceIn(column.widthLimits.minimumDp, column.widthLimits.maximumDp)
    return when (column) {
        TrafficColumn.SERIAL_NUMBER -> copy(serialNumberDp = width)
        TrafficColumn.TIMESTAMP -> copy(timestampDp = width)
        TrafficColumn.METHOD -> copy(methodDp = width)
        TrafficColumn.PROTOCOL -> copy(protocolDp = width)
        TrafficColumn.STREAM -> copy(streamDp = width)
        TrafficColumn.SOURCE -> copy(sourceDp = width)
        TrafficColumn.HOST -> copy(hostDp = width)
        TrafficColumn.PATH -> copy(pathDp = width)
        TrafficColumn.STATUS -> copy(statusDp = width)
        TrafficColumn.SIZE -> copy(sizeDp = width)
        TrafficColumn.DURATION -> copy(durationDp = width)
        TrafficColumn.TYPE -> copy(typeDp = width)
    }
}

/** Restores one column while preserving every other user-selected width. */
internal fun TrafficTableColumnWidths.resetColumn(column: TrafficColumn): TrafficTableColumnWidths {
    val defaults = TrafficTableColumnWidths()
    return when (column) {
        TrafficColumn.SERIAL_NUMBER -> copy(serialNumberDp = defaults.serialNumberDp)
        TrafficColumn.TIMESTAMP -> copy(timestampDp = defaults.timestampDp)
        TrafficColumn.METHOD -> copy(methodDp = defaults.methodDp)
        TrafficColumn.PROTOCOL -> copy(protocolDp = defaults.protocolDp)
        TrafficColumn.STREAM -> copy(streamDp = defaults.streamDp)
        TrafficColumn.SOURCE -> copy(sourceDp = defaults.sourceDp)
        TrafficColumn.HOST -> copy(hostDp = defaults.hostDp)
        TrafficColumn.PATH -> copy(pathDp = null)
        TrafficColumn.STATUS -> copy(statusDp = defaults.statusDp)
        TrafficColumn.SIZE -> copy(sizeDp = defaults.sizeDp)
        TrafficColumn.DURATION -> copy(durationDp = defaults.durationDp)
        TrafficColumn.TYPE -> copy(typeDp = defaults.typeDp)
    }
}

/**
 * Resolves fixed columns and Path auto-fill against the current viewport.
 *
 * Explicit widths are never silently shrunk. When their sum exceeds the viewport, [tableWidthDp] grows and the
 * caller exposes horizontal scrolling. Path consumes remaining space only while its stored width is null.
 */
internal fun resolveTrafficColumnLayout(
    widths: TrafficTableColumnWidths,
    visibility: ColumnVisibilityState,
    viewportWidthDp: Float,
): ResolvedTrafficColumnLayout {
    val visibleColumns = TrafficColumn.entries.filter(visibility::isVisible)
    val nonPathWidths = visibleColumns
        .filterNot { column -> column == TrafficColumn.PATH }
        .associateWith { column -> widths.storedWidthDp(column) }
    val remainingPathWidth = viewportWidthDp - TRAFFIC_TABLE_HORIZONTAL_PADDING_DP - nonPathWidths.values.sum()
    val pathWidth = widths.pathDp
        ?.coerceIn(TrafficColumn.PATH.widthLimits.minimumDp, TrafficColumn.PATH.widthLimits.maximumDp)
        ?: remainingPathWidth.coerceIn(
            TrafficColumn.PATH.widthLimits.minimumDp,
            TrafficColumn.PATH.widthLimits.maximumDp,
        )
    val resolvedWidths = buildMap {
        visibleColumns.forEach { column ->
            put(column, if (column == TrafficColumn.PATH) pathWidth else requireNotNull(nonPathWidths[column]))
        }
    }
    val contentWidth = resolvedWidths.values.sum() + TRAFFIC_TABLE_HORIZONTAL_PADDING_DP
    return ResolvedTrafficColumnLayout(
        widthsDp = resolvedWidths,
        tableWidthDp = maxOf(viewportWidthDp, contentWidth),
    )
}

private fun TrafficTableColumnWidths.storedWidthDp(column: TrafficColumn): Float = when (column) {
    TrafficColumn.SERIAL_NUMBER -> serialNumberDp
    TrafficColumn.TIMESTAMP -> timestampDp
    TrafficColumn.METHOD -> methodDp
    TrafficColumn.PROTOCOL -> protocolDp
    TrafficColumn.STREAM -> streamDp
    TrafficColumn.SOURCE -> sourceDp
    TrafficColumn.HOST -> hostDp
    TrafficColumn.PATH -> pathDp ?: TrafficColumn.PATH.widthLimits.minimumDp
    TrafficColumn.STATUS -> statusDp
    TrafficColumn.SIZE -> sizeDp
    TrafficColumn.DURATION -> durationDp
    TrafficColumn.TYPE -> typeDp
}.coerceIn(column.widthLimits.minimumDp, column.widthLimits.maximumDp)

private val TrafficColumn.widthLimits: TrafficColumnWidthLimits
    get() = when (this) {
        TrafficColumn.SERIAL_NUMBER -> TrafficColumnWidthLimits(40f, 120f)
        TrafficColumn.TIMESTAMP -> TrafficColumnWidthLimits(96f, 240f)
        TrafficColumn.METHOD -> TrafficColumnWidthLimits(64f, 160f)
        TrafficColumn.PROTOCOL -> TrafficColumnWidthLimits(76f, 180f)
        TrafficColumn.STREAM -> TrafficColumnWidthLimits(64f, 180f)
        TrafficColumn.SOURCE -> TrafficColumnWidthLimits(88f, 240f)
        TrafficColumn.HOST -> TrafficColumnWidthLimits(120f, 520f)
        TrafficColumn.PATH -> TrafficColumnWidthLimits(160f, 1_200f)
        TrafficColumn.STATUS -> TrafficColumnWidthLimits(72f, 180f)
        TrafficColumn.SIZE -> TrafficColumnWidthLimits(64f, 180f)
        TrafficColumn.DURATION -> TrafficColumnWidthLimits(72f, 180f)
        TrafficColumn.TYPE -> TrafficColumnWidthLimits(64f, 180f)
    }

private const val TRAFFIC_TABLE_HORIZONTAL_PADDING_DP = 16f
