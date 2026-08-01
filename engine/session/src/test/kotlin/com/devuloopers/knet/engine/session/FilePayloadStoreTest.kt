package com.devuloopers.knet.engine.session

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilePayloadStoreTest {

    private lateinit var payloadStore: FilePayloadStore

    @BeforeTest
    fun setUp() {
        val tempDir = TestFixtures.createTempDir()
        payloadStore = FilePayloadStore(tempDir)
    }

    @Test
    fun testSaveAndLoadPayload() {
        val bytes = "hello_world".toByteArray(Charsets.UTF_8)
        val path = payloadStore.savePayload("tx1", "req", bytes)
        assertNotNull(path)

        val loaded = payloadStore.loadPayload(path)
        assertNotNull(loaded)
        assertEquals("hello_world", String(loaded, Charsets.UTF_8))
    }

    @Test
    fun testDeletePayload() {
        val bytes = "delete_me".toByteArray(Charsets.UTF_8)
        val path = payloadStore.savePayload("tx2", "res", bytes)
        assertNotNull(path)

        val deleted = payloadStore.deletePayload(path)
        assertTrue(deleted)
        assertNull(payloadStore.loadPayload(path))
    }

    @Test
    fun testClearStore() {
        payloadStore.savePayload("tx3", "req", "data1".toByteArray())
        payloadStore.savePayload("tx4", "res", "data2".toByteArray())

        payloadStore.clearStore()
    }
}
