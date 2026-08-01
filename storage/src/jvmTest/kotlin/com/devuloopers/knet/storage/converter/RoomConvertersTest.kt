package com.devuloopers.knet.storage.converter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying [RoomConverters] string list conversions.
 */
class RoomConvertersTest {

    @Test
    fun testFromStringListAndToStringListRoundtrip() {
        val original = listOf("Header1: Value1", "Header2: Value2")
        val serialized = RoomConverters.fromStringList(original)
        val deserialized = RoomConverters.toStringList(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun testToStringListWithBlankReturnsEmptyList() {
        val result = RoomConverters.toStringList("")
        assertTrue(result.isEmpty())
    }
}
