package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrafficColumnLayoutTest {

    @Test
    fun `default path width fills the remaining viewport`() {
        val layout = resolveTrafficColumnLayout(
            widths = TrafficTableColumnWidths(),
            visibility = ColumnVisibilityState(),
            viewportWidthDp = 1_000f,
        )

        assertEquals(314f, layout.widthDp(TrafficColumn.PATH))
        assertEquals(1_000f, layout.tableWidthDp)
    }

    @Test
    fun `explicit path width produces horizontal overflow without shrinking columns`() {
        val layout = resolveTrafficColumnLayout(
            widths = TrafficTableColumnWidths(pathDp = 500f),
            visibility = ColumnVisibilityState(),
            viewportWidthDp = 900f,
        )

        assertEquals(500f, layout.widthDp(TrafficColumn.PATH))
        assertEquals(1_186f, layout.tableWidthDp)
    }

    @Test
    fun `resize requests are constrained per column`() {
        val defaults = TrafficTableColumnWidths()

        assertEquals(
            120f,
            defaults.withColumnWidth(TrafficColumn.HOST, requestedWidthDp = 5f).hostDp,
        )
        assertEquals(
            520f,
            defaults.withColumnWidth(TrafficColumn.HOST, requestedWidthDp = 2_000f).hostDp,
        )
    }

    @Test
    fun `resetting path restores auto fill without changing other columns`() {
        val resized = TrafficTableColumnWidths(hostDp = 300f, pathDp = 640f)

        val reset = resized.resetColumn(TrafficColumn.PATH)

        assertNull(reset.pathDp)
        assertEquals(300f, reset.hostDp)
    }
}
