package com.devuloopers.knet.data.desktop.networkconditions

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionSettingsEntity
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Real SQLite transactions qualify publication, cross-instance writers and rollback rather than a mocked DAO. */
class NetworkConditionAtomicSnapshotTest {
    @Test fun `atomic multi-table replacement never publishes a disabled or mixed policy`() = runTest {
        fixture { database, scope ->
            val repository = RoomNetworkConditionsRepository(database, scope)
            val first = NetworkConditionProfile(NetworkConditionProfileId("snapshot-0"), "Snapshot zero")
            repository.upsertProfile(first)
            repository.activateGlobalProfile(first.id)
            repository.configuration.first { it.enabled }
            val emitted = CopyOnWriteArrayList<NetworkConditionConfiguration>()
            val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                repository.configuration.collect { emitted += it }
            }
            val dao = database.networkConditionDao()
            val template = dao.getProfiles().single()
            repeat(50) { index ->
                database.useWriterConnection { connection -> connection.immediateTransaction {
                    val nextId = "snapshot-${index + 1}"
                    // The state is invalid inside the transaction until its profile is inserted.
                    dao.upsertSettings(NetworkConditionSettingsEntity(enabled = true,
                        globalProfileId = nextId, lastQuickAddProfileId = nextId))
                    dao.deleteProfile("snapshot-$index")
                    delay(2)
                    dao.upsertProfile(template.copy(id = nextId, name = "Snapshot ${index + 1}"))
                } }
                repository.configuration.first { it.globalProfileId?.value == "snapshot-${index + 1}" }
            }
            collector.cancelAndJoin()
            assertTrue(emitted.size >= 2)
            emitted.forEach { state ->
                assertTrue(state.enabled, "A committed valid policy must never transiently fail closed")
                assertEquals(state.globalProfileId, state.customProfiles.single().id)
                assertTrue(state.lastQuickAddProfileId == null || state.lastQuickAddProfileId == state.globalProfileId)
            }
        }
    }

    @Test fun `independent repository instances cannot lose concurrent settings edits`() = runTest {
        fixture { database, scope ->
            val first = RoomNetworkConditionsRepository(database, scope)
            val second = RoomNetworkConditionsRepository(database, scope)
            val profile = NetworkConditionProfile(NetworkConditionProfileId("concurrent"), "Concurrent")
            first.upsertProfile(profile)
            repeat(30) {
                first.setEnabled(false); first.setGlobalProfile(null); first.setLastQuickAddProfile(null)
                coroutineScope {
                    val activation = async(Dispatchers.Default) { first.activateGlobalProfile(profile.id) }
                    val preference = async(Dispatchers.Default) { second.setLastQuickAddProfile(profile.id) }
                    activation.await(); preference.await()
                }
                val stored = database.networkConditionDao().getSettings()!!
                assertTrue(stored.enabled)
                assertEquals(profile.id.value, stored.globalProfileId)
                assertEquals(profile.id.value, stored.lastQuickAddProfileId)
            }
        }
    }

    @Test fun `cancelling an outer transaction rolls back nested policy edits without publication`() = runTest {
        fixture { database, scope ->
            val repository = RoomNetworkConditionsRepository(database, scope)
            val profile = NetworkConditionProfile(NetworkConditionProfileId("rollback"), "Rollback")
            repository.upsertProfile(profile)
            repository.configuration.first { it.customProfiles.isNotEmpty() }
            val entered = CompletableDeferred<Unit>()
            val writer = scope.launch {
                NetworkConditionTransactions(database).write {
                    repository.activateGlobalProfile(profile.id)
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            entered.await()
            assertFalse(repository.configuration.value.enabled)
            writer.cancelAndJoin()
            assertEquals(null, database.networkConditionDao().getSettings())
            assertFalse(repository.configuration.value.enabled)
            // The writer connection and transaction owner must remain usable after rollback.
            repository.activateGlobalProfile(profile.id)
            assertTrue(repository.configuration.first { it.enabled }.enabled)
        }
    }

    private suspend fun fixture(block: suspend (com.devuloopers.knet.storage.database.KNetDatabase, CoroutineScope) -> Unit) {
        val root = Files.createTempDirectory("knet-policy-atomic-").toFile()
        val database = DatabaseFactory.create(root.resolve("knet.db"))
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        try { block(database, scope) } finally { job.cancelAndJoin(); database.close(); root.deleteRecursively() }
    }
}
