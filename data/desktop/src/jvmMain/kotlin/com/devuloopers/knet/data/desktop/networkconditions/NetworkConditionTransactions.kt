package com.devuloopers.knet.data.desktop.networkconditions

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Owns policy transactions with the caller's job while retaining Room's confined connection and dispatcher.
 * Room's connection helpers enter its database scope; restoring the operation owner inside that boundary ensures
 * cancellation rolls back suspended work instead of leaving it alive under the database's longer-lived job.
 */
internal class NetworkConditionTransactions(private val database: KNetDatabase) {
    /** Reads all policy tables from one committed SQLite snapshot; cancellation cannot publish a partial result. */
    suspend fun <Result> read(block: suspend () -> Result): Result {
        val owner = currentCoroutineContext().also { it.ensureActive() }.job
        return database.useReaderConnection { connection ->
            withContext(owner) { connection.deferredTransaction { block() } }
        }
    }

    /** Serializes validation and mutation across all repository instances and rolls back on caller cancellation. */
    suspend fun <Result> write(block: suspend () -> Result): Result {
        val owner = currentCoroutineContext().also { it.ensureActive() }.job
        return database.useWriterConnection { connection ->
            withContext(owner) { connection.immediateTransaction { block() } }
        }
    }
}
