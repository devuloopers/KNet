package com.devuloopers.knet.engine.script.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentStoreTest {

    @Test
    fun testStoreGetSetRemoveClearSnapshot() {
        val store = EnvironmentStore(mapOf("initial" to "value"))
        assertEquals("value", store["initial"])

        store["key1"] = "val1"
        assertEquals("val1", store["key1"])

        store.putAll(mapOf("key2" to "val2", "key3" to "val3"))
        assertEquals("val2", store["key2"])
        assertEquals("val3", store["key3"])

        assertTrue(store.has("key1"))

        store.remove("key1")
        assertNull(store["key1"])

        val snapshot = store.snapshot()
        assertEquals("val2", snapshot["key2"])

        store.clear()
        assertTrue(store.snapshot().isEmpty())
    }
}
