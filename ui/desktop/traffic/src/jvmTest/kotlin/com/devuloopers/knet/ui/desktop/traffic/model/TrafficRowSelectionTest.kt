package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.contract.traffic.TrafficRecordId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import kotlin.test.*

/** Exact-identity selection regressions, independent of pointer devices and live storage. */
class TrafficRowSelectionTest {
    private val rows = (0..5).map { TrafficRecordId.Exchange(ExchangeId("item-$it")) }

    @Test fun `context selection replaces unrelated rows and preserves selected groups and range anchors`() {
        val initial = TrafficRowSelection().initialize(rows[0])
        val group = initial.select(rows[2], TrafficSelectionGesture.TOGGLE, rows)!!
        assertEquals(group, group.select(rows[0], TrafficSelectionGesture.CONTEXT, rows))
        val unrelated = group.select(rows[4], TrafficSelectionGesture.CONTEXT, rows)!!
        assertEquals(setOf(rows[4]), unrelated.records)
        assertEquals(rows[4], unrelated.anchor)
        assertEquals(setOf(rows[1]), group.clear().select(rows[1], TrafficSelectionGesture.CONTEXT, rows)!!.records)
        val opaque = TrafficRecordId.Opaque(OpaqueFlowId("item-0"))
        assertEquals(setOf(opaque), initial.select(opaque, TrafficSelectionGesture.CONTEXT, listOf(rows[0], opaque))!!.records)
    }

    @Test fun `initial focus selects one typed record once and establishes the range anchor`() {
        for (first in listOf(rows[0], TrafficRecordId.Opaque(OpaqueFlowId("item-0")))) {
            val empty = TrafficRowSelection()
            assertEquals(empty, empty.initialize(null))
            val initialized = empty.initialize(first)
            assertEquals(setOf(first), initialized.records)
            assertEquals(first, initialized.anchor)
            assertTrue(initialized.initialized)
            assertEquals(initialized, initialized.initialize(rows[1]))
            assertEquals(setOf(first, rows[1], rows[2]), initialized.select(rows[2], TrafficSelectionGesture.RANGE,
                listOf(first, rows[1], rows[2]))!!.records)
        }
    }

    @Test fun `explicit clear and toggling the last selected row off survive automatic projections`() {
        val initial = TrafficRowSelection().initialize(rows[0])
        for (empty in listOf(initial.clear(), initial.select(rows[0], TrafficSelectionGesture.TOGGLE, rows)!!)) {
            assertTrue(empty.records.isEmpty())
            assertTrue(empty.initialized)
            assertEquals(empty, empty.initialize(rows[0]))
            assertEquals(empty, empty.initialize(rows[1]))
        }
    }

    @Test fun `replace toggle and deselect preserve exact identities`() {
        var selection = TrafficRowSelection().select(rows[1], TrafficSelectionGesture.REPLACE, rows)!!
        selection = selection.select(rows[3], TrafficSelectionGesture.TOGGLE, rows)!!
        assertEquals(setOf(rows[1], rows[3]), selection.records)
        selection = selection.select(rows[1], TrafficSelectionGesture.TOGGLE, rows)!!
        assertEquals(setOf(rows[3]), selection.records)
        assertEquals(setOf(rows[5]), selection.select(rows[5], TrafficSelectionGesture.REPLACE, rows)!!.records)
    }

    @Test fun `range is inclusive in either direction and can extend an existing selection`() {
        val selected = TrafficRowSelection().select(rows[4], TrafficSelectionGesture.REPLACE, rows)!!
        assertEquals(rows.subList(1, 5).toSet(), selected.select(rows[1], TrafficSelectionGesture.RANGE, rows)!!.records)
        val extra = selected.copy(records = selected.records + rows[0])
        assertEquals(setOf(rows[0], rows[3], rows[4]), extra.select(rows[3], TrafficSelectionGesture.EXTEND_RANGE, rows)!!.records)
    }

    @Test fun `page eviction and arrivals do not mutate an explicit selection`() {
        val selected = TrafficRowSelection(setOf(rows[0], rows[4]), rows[0])
        val newer = TrafficRecordId.Exchange(ExchangeId("new"))
        val page = listOf(newer, rows[3], rows[4], rows[5])
        val replacement = selected.select(rows[5], TrafficSelectionGesture.RANGE, page)!!
        assertEquals(setOf(rows[5]), replacement.records)
        assertEquals(rows[5], replacement.anchor)
        assertEquals(setOf(rows[3], rows[4], rows[5]), replacement.select(rows[3], TrafficSelectionGesture.RANGE, page)!!.records)
        assertEquals(setOf(rows[0], rows[4]), selected.records)
        assertEquals(selected, selected.select(rows[2], TrafficSelectionGesture.TOGGLE, page))
    }

    @Test fun `HTTP and opaque records with identical text ids remain distinct`() {
        val http = TrafficRecordId.Exchange(ExchangeId("same"))
        val opaque = TrafficRecordId.Opaque(OpaqueFlowId("same"))
        val selected = TrafficRowSelection(setOf(http), http).select(opaque, TrafficSelectionGesture.TOGGLE, listOf(http, opaque))!!
        assertEquals(2, selected.records.size)
    }

    @Test fun `overflow is rejected without silently trimming existing selection`() {
        val many = (0 until TrafficRowSelection.MAXIMUM_ITEMS).map { TrafficRecordId.Exchange(ExchangeId("row-$it")) }.toSet()
        val extra = TrafficRecordId.Exchange(ExchangeId("extra"))
        val selected = TrafficRowSelection(many)
        assertNull(selected.select(extra, TrafficSelectionGesture.TOGGLE, listOf(extra)))
        assertEquals(TrafficRowSelection.MAXIMUM_ITEMS, selected.records.size)
    }
}
