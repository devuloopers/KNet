package com.devuloopers.knet.ui.desktop.codeeditor.viewport

/**
 * Immutable document geometry independent of the currently composed rows.
 *
 * Ordinary rows share [baselineHeight]; only exceptions retain an index and a cumulative height delta.
 * Long totals prevent overflow without retaining text layouts or a height object for every source line.
 */
internal class EditorLineHeightIndex private constructor(
    val rowCount: Int,
    val baselineHeight: Int,
    private val exceptionRows: IntArray,
    private val cumulativeDeltas: LongArray,
) {
    val totalHeight: Long get() = prefixHeight(rowCount)
    val retainedArrayBytes: Int get() = exceptionRows.size * (Int.SIZE_BYTES + Long.SIZE_BYTES)

    /** Pixel offset preceding [row], including the sentinel immediately after the final row. */
    fun prefixHeight(row: Int): Long {
        require(row in 0..rowCount)
        var low = 0
        var high = exceptionRows.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (exceptionRows[middle] < row) low = middle + 1 else high = middle
        }
        return row.toLong() * baselineHeight + if (low == 0) 0 else cumulativeDeltas[low - 1]
    }

    /** Resolves an absolute document offset to a lazy row and an offset inside that row. */
    fun locate(offset: Double): EditorScrollPosition {
        val target = offset.coerceIn(0.0, (totalHeight - 1).toDouble())
        var low = 0
        var high = rowCount
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (prefixHeight(middle) <= target) low = middle else high = middle
        }
        return EditorScrollPosition(low, (target - prefixHeight(low)).toInt())
    }

    companion object {
        /** Builds sparse geometry; [heightAt] may check cancellation while measuring each source row. */
        fun build(rowCount: Int, baselineHeight: Int, heightAt: (Int) -> Int): EditorLineHeightIndex {
            require(rowCount > 0 && baselineHeight > 0)
            var rows = IntArray(0)
            var deltas = LongArray(0)
            var count = 0
            var delta = 0L
            repeat(rowCount) { row ->
                val height = heightAt(row)
                require(height > 0)
                if (height != baselineHeight) {
                    if (count == rows.size) {
                        val capacity = (count * 2).coerceAtLeast(8)
                        rows = rows.copyOf(capacity)
                        deltas = deltas.copyOf(capacity)
                    }
                    delta += height.toLong() - baselineHeight
                    rows[count] = row
                    deltas[count++] = delta
                }
            }
            return EditorLineHeightIndex(rowCount, baselineHeight, rows.copyOf(count), deltas.copyOf(count))
        }
    }
}

/** Lazy list destination in physical pixels. */
internal data class EditorScrollPosition(val row: Int, val offset: Int)
