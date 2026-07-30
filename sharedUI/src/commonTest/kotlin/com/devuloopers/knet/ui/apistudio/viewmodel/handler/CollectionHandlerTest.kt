package com.devuloopers.knet.ui.apistudio.viewmodel.handler

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit test suite for [CollectionHandler].
 */
class CollectionHandlerTest {

    private val handler = CollectionHandler()

    @Test
    fun testCreateCollection() {
        val initialCols = emptyList<ApiCollection>()
        val (updated, newCol) = handler.createCollection(initialCols, "My Test Collection", "Desc")

        assertEquals(1, updated.size)
        assertEquals("My Test Collection", newCol.name)
        assertNotNull(newCol.id)
    }

    @Test
    fun testCreateFolder() {
        val (cols, col) = handler.createCollection(emptyList(), "Col")
        val updatedCols = handler.createFolder(cols, col.id, "Auth Folder")

        val targetCol = updatedCols.find { it.id == col.id }
        assertNotNull(targetCol)
        assertEquals(1, targetCol.folders.size)
        assertEquals("Auth Folder", targetCol.folders.first().name)
    }
}
