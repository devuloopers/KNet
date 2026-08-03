package com.devuloopers.knet.ui.core

import com.devuloopers.knet.ui.core.components.selection.SelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying KNet Design System v2.0 type-safe selection models.
 */
class SelectionModelTest {

    @Test
    fun testSingleSelection() {
        val model = SelectionModel<String>(isMultiSelectionAllowed = false)
        val selected = model.select("item1").select("item2")

        assertEquals(setOf("item2"), selected.selectedItems)
        assertTrue(selected.isSelected("item2"))
        assertFalse(selected.isSelected("item1"))
    }

    @Test
    fun testMultiSelection() {
        val model = SelectionModel<String>(isMultiSelectionAllowed = true)
        val selected = model.select("item1").select("item2")

        assertEquals(setOf("item1", "item2"), selected.selectedItems)
        assertTrue(selected.isSelected("item1"))
        assertTrue(selected.isSelected("item2"))
    }

    @Test
    fun testToggleAndClear() {
        var model = SelectionModel<Int>(isMultiSelectionAllowed = true)
        model = model.toggle(100)
        assertTrue(model.isSelected(100))

        model = model.toggle(100)
        assertFalse(model.isSelected(100))

        model = model.select(1).select(2).clear()
        assertTrue(model.selectedItems.isEmpty())
    }
}
