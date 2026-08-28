package com.devuloopers.knet.companion.data.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompanionDataStoreFileTest {
    @Test
    fun persistedRegistrationRestoresAfterDataStoreScopeRestart(): Unit = runBlocking {
        withTemporaryDataStorePath { path ->
            val firstJob = SupervisorJob()
            val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
            val firstDataStore = createCompanionDataStore(path, firstScope)
            val firstStore = DataStoreCompanionRecordStore.open(firstDataStore, firstScope)
            firstStore.write("durable-registration")
            assertEquals("durable-registration", firstDataStore.data.first().asMap().values.single())
            firstJob.cancelAndJoin()

            val secondJob = SupervisorJob()
            val secondScope = CoroutineScope(secondJob + Dispatchers.IO)
            try {
                val restoredDataStore = createCompanionDataStore(path, secondScope)
                val restoredStore = DataStoreCompanionRecordStore.open(restoredDataStore, secondScope)
                assertEquals("durable-registration", restoredStore.content.value)
            } finally {
                secondJob.cancelAndJoin()
            }
        }
    }

    @Test
    fun corruptPreferenceFileRecoversToEmptyFailClosedState(): Unit = runBlocking {
        withTemporaryDataStorePath { path ->
            FileSystem.SYSTEM.write(path) { writeUtf8("not-a-preferences-protobuf") }
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.IO)
            try {
                val dataStore = createCompanionDataStore(path, scope)
                val store = DataStoreCompanionRecordStore.open(dataStore, scope)
                assertNull(store.content.value)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private suspend fun withTemporaryDataStorePath(block: suspend (Path) -> Unit) {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "knet-companion-${kotlin.random.Random.nextLong()}"
        FileSystem.SYSTEM.createDirectories(directory)
        try {
            block(directory / COMPANION_DATA_STORE_FILE_NAME)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }
}
