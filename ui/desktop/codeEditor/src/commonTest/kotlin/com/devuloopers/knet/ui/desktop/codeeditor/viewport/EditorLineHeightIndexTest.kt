package com.devuloopers.knet.ui.desktop.codeeditor.viewport

import kotlin.test.*

/** Regression coverage for stable pixel-to-row mapping across exceptionally tall logical lines. */
class EditorLineHeightIndexTest {
    @Test fun `one huge field does not change geometry as viewport enters or leaves it`() {
        val heights = IntArray(566) { if (it == 395) 80_000 else 16 }
        val index = EditorLineHeightIndex.build(heights.size, 16) { heights[it] }
        assertEquals(12, index.retainedArrayBytes)
        var prefix = 0L
        heights.forEachIndexed { row, height ->
            assertEquals(prefix, index.prefixHeight(row))
            for (inside in listOf(0, height / 2, height - 1)) {
                assertEquals(EditorScrollPosition(row, inside), index.locate((prefix + inside).toDouble()))
            }
            prefix += height
        }
        assertEquals(prefix, index.totalHeight)
        assertEquals(EditorScrollPosition(0, 0), index.locate(-100.0))
        assertEquals(EditorScrollPosition(565, 15), index.locate(Double.MAX_VALUE))
    }

    @Test fun `uniform hundred thousand row document retains no per-row geometry arrays`() {
        val index = EditorLineHeightIndex.build(100_000, 16) { 16 }
        assertEquals(0, index.retainedArrayBytes)
        assertEquals(1_600_000L, index.totalHeight)
        assertEquals(EditorScrollPosition(75_000, 8), index.locate(1_200_008.0))
    }

    @Test fun `cumulative heights support totals beyond signed integer pixels`() {
        val index = EditorLineHeightIndex.build(4, 16) { 1_000_000_000 }
        assertEquals(4_000_000_000L, index.totalHeight)
        assertEquals(EditorScrollPosition(3, 900_000_000), index.locate(3_900_000_000.0))
    }

    @Test fun `mixed short and tall rows map monotonically in both directions`() {
        val heights = intArrayOf(16, 2000, 12, 31, 16, 50_000, 16)
        val index = EditorLineHeightIndex.build(heights.size, 16) { heights[it] }
        for (offset in 0 until index.totalHeight step 7) {
            val position = index.locate(offset.toDouble())
            assertEquals(offset, index.prefixHeight(position.row) + position.offset)
        }
        for (offset in (index.totalHeight - 1) downTo 0 step 13) {
            val position = index.locate(offset.toDouble())
            assertEquals(offset, index.prefixHeight(position.row) + position.offset)
        }
    }

    @Test fun `invalid empty or zero height geometry is rejected`() {
        assertFailsWith<IllegalArgumentException> { EditorLineHeightIndex.build(0, 16) { 16 } }
        assertFailsWith<IllegalArgumentException> { EditorLineHeightIndex.build(1, 0) { 16 } }
        assertFailsWith<IllegalArgumentException> { EditorLineHeightIndex.build(1, 16) { 0 } }
    }
}
