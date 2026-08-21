package com.devuloopers.knet.domain.workspace.model

/**
 * Persisted logical widths for the desktop Traffic table.
 *
 * Values are density-independent pixel counts so the common workspace contract does not depend on Compose.
 * A null [pathDp] keeps Path in automatic fill mode until the user explicitly resizes it.
 *
 * @property serialNumberDp Serial-number column width.
 * @property timestampDp Timestamp column width.
 * @property methodDp semantic method column width.
 * @property protocolDp Effective wire-protocol column width.
 * @property streamDp Multiplexed stream identifier column width.
 * @property sourceDp Capture-origin column width.
 * @property hostDp Host column width.
 * @property pathDp Explicit Path width, or null to fill the remaining viewport width.
 * @property statusDp Status column width.
 * @property sizeDp transferred-size column width.
 * @property durationDp Duration column width.
 * @property typeDp Payload-type column width.
 * @throws IllegalArgumentException When a stored width is non-finite or not positive.
 */
data class TrafficTableColumnWidths(
    val serialNumberDp: Float = 48f,
    val timestampDp: Float = 130f,
    val methodDp: Float = 76f,
    val protocolDp: Float = 92f,
    val streamDp: Float = 76f,
    val sourceDp: Float = 112f,
    val hostDp: Float = 180f,
    val pathDp: Float? = null,
    val statusDp: Float = 84f,
    val sizeDp: Float = 76f,
    val durationDp: Float = 76f,
    val typeDp: Float = 64f,
) {
    init {
        listOfNotNull(
            serialNumberDp,
            timestampDp,
            methodDp,
            protocolDp,
            streamDp,
            sourceDp,
            hostDp,
            pathDp,
            statusDp,
            sizeDp,
            durationDp,
            typeDp,
        ).forEach { width ->
            require(width.isFinite() && width > 0f) { "Traffic table column widths must be finite and positive." }
        }
    }
}
