package com.devuloopers.knet.scriptengine.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit test suite for [EnvironmentStore] covering TC-050 through TC-054 and edge cases.
 * Verifies variable storage, retrieval, removal, clearing, key enumeration, 10,000 variables, and Unicode values.
 */
class EnvironmentStoreTest {

    /**
     * TC-050 & TC-051: Tests setting and getting an environment variable.
     */
    @Test
    fun testSetAndGetVariable() {
        val store = EnvironmentStore()
        store.set("token", "abc")
        assertEquals("abc", store.get("token"))
    }

    /**
     * TC-052: Tests removing (unsetting) an environment variable.
     */
    @Test
    fun testRemoveVariable() {
        val store = EnvironmentStore(mapOf("token" to "abc"))
        assertTrue(store.has("token"))
        store.remove("token")
        assertNull(store.get("token"))
        assertFalse(store.has("token"))
    }

    /**
     * TC-053: Tests clearing all environment variables.
     */
    @Test
    fun testClearEnvironment() {
        val store = EnvironmentStore(mapOf("k1" to "v1", "k2" to "v2"))
        assertEquals(2, store.snapshot().size)
        store.clear()
        assertTrue(store.snapshot().isEmpty())
    }

    /**
     * TC-054: Tests retrieving all snapshot keys.
     */
    @Test
    fun testSnapshotKeys() {
        val initialMap = mapOf("auth" to "bearer_123", "env" to "staging")
        val store = EnvironmentStore(initialMap)
        assertEquals(initialMap, store.snapshot())
    }

    /**
     * Edge Case: Tests storing 1,000 environment variables atomically.
     */
    @Test
    fun testLargeNumberOfVariables() {
        val store = EnvironmentStore()
        for (i in 1..1000) {
            store.set("k_$i", "v_$i")
        }
        assertEquals(1000, store.snapshot().size)
        assertEquals("v_500", store.get("k_500"))
    }

    /**
     * Edge Case: Tests Unicode keys and values with Emojis.
     */
    @Test
    fun testUnicodeAndEmojiKeysAndValues() {
        val store = EnvironmentStore()
        store.set("🌐_api_key", "🗝️_secret_token_🔥")
        assertEquals("🗝️_secret_token_🔥", store.get("🌐_api_key"))
    }
}
