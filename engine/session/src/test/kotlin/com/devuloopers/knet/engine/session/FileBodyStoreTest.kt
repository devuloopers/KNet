package com.devuloopers.knet.engine.session

import com.devuloopers.knet.application.contract.traffic.BodyFinalizeResult
import com.devuloopers.knet.application.contract.traffic.BodyDeleteResult
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.BodyWritePolicy
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Tests atomic, bounded, opaque canonical body storage. */
class FileBodyStoreTest {

    /** Verifies finalized objects expose only bounded opaque inventory keys and can be reconciled by key. */
    @Test
    fun `finalized object inventory is stable bounded and path opaque`() = runTest {
        val root = Files.createTempDirectory("knet-body-inventory-").toFile()
        try {
            val store = FileBodyStore(root)
            val bodyIds = listOf(BodyId("inventory-a"), BodyId("inventory-b"), BodyId("inventory-c"))
            bodyIds.forEach { bodyId ->
                val writer = store.openWrite(bodyId, BodyWritePolicy(16L))
                writer.append(bodyId.value.toByteArray())
                writer.complete()
            }

            val first = store.inventoryFinalizedObjects(after = null, limit = 2)
            val second = store.inventoryFinalizedObjects(after = first.nextCursor, limit = 2)
            val inventoried = (first.keys + second.keys).map { key -> key.value }

            assertEquals(bodyIds.map { store.storageKey(it).value }.sorted(), inventoried)
            assertEquals(2, first.keys.size)
            assertTrue(first.nextCursor != null)
            assertEquals(1, second.keys.size)
            assertEquals(null, second.nextCursor)
            assertEquals(BodyDeleteResult.DELETED, store.deleteByStorageKey(first.keys.first()))
        } finally {
            root.deleteRecursively()
        }
    }

    /** Verifies finalization, truncation, digest metadata, and bounded range access. */
    @Test
    fun `body write finalizes atomically with explicit truncation`() = runTest {
        val root = Files.createTempDirectory("knet-body-store-").toFile()
        try {
            val store = FileBodyStore(root)
            val writer = store.openWrite(
                bodyId = BodyId("../../secret-body"),
                policy = BodyWritePolicy(maximumStoredBytes = 5L, maximumChunkBytes = 8),
            )

            val append = writer.append("abcdefgh".toByteArray())
            val finalized = writer.complete()
            val body = finalized.body

            assertEquals(8L, append.observedBytes)
            assertEquals(5L, append.storedBytes)
            assertTrue(append.truncated)
            assertIs<BodyCaptureOutcome.Truncated>(body.outcome)
            assertEquals(64, body.digest?.value?.length)
            assertEquals("cde", store.readBody(body.id, BodyRange(offset = 2, length = 3)).copyBytes().decodeToString())
            val storedNames = root.walkTopDown().filter { it.isFile }.map { it.name }.toList()
            assertTrue(storedNames.none { it.contains("secret") || it.contains("..") })
            assertTrue(root.resolve("tmp").listFiles().isNullOrEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    /** Verifies duplicate active writers are rejected and abort removes temporary content. */
    @Test
    fun `one body id has one terminal writer`() = runTest {
        val root = Files.createTempDirectory("knet-body-writer-").toFile()
        try {
            val store = FileBodyStore(root)
            val id = BodyId("body-one")
            val writer = store.openWrite(id, BodyWritePolicy(10L))

            assertFailsWith<IllegalStateException> {
                store.openWrite(id, BodyWritePolicy(10L))
            }
            writer.append("partial".toByteArray())
            val aborted = writer.abort(BodyCaptureOutcome.Failed(com.devuloopers.knet.traffic.model.body.BodyFailure.SourceFailed))

            assertIs<BodyFinalizeResult.Unavailable>(aborted)
            assertTrue(root.resolve("tmp").listFiles().isNullOrEmpty())
            assertFailsWith<IllegalStateException> {
                store.readBody(id, BodyRange(0L, 1))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    /** Verifies startup reconciliation removes abandoned temporary objects only. */
    @Test
    fun `temporary reconciliation removes abandoned writes`() = runTest {
        val root = Files.createTempDirectory("knet-body-reconcile-").toFile()
        try {
            val store = FileBodyStore(root)
            val abandoned = root.resolve("tmp/abandoned.tmp").apply { writeText("partial") }
            val unrelated = root.resolve("tmp/keep.txt").apply { writeText("not-a-body-temp") }

            assertEquals(1, store.reconcileTemporaryObjects())
            assertFalse(abandoned.exists())
            assertTrue(unrelated.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
