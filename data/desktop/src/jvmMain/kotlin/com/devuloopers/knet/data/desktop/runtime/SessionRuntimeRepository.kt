package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.domain.network.model.HttpTransaction
import com.devuloopers.knet.engine.session.FilePayloadStore
import com.devuloopers.knet.engine.session.SessionManager
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Desktop runtime coordinator managing live transaction streaming and payload file persistence.
 */
public class SessionRuntimeRepository(
    private val database: KNetDatabase,
    private val baseDir: File
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val payloadStore = FilePayloadStore(File(baseDir, "payloads"))
    private val sessionManager = SessionManager(database, payloadStore)

    private val _liveTransactions = MutableSharedFlow<HttpTransaction>(extraBufferCapacity = 64)
    public val liveTransactions: Flow<HttpTransaction> = _liveTransactions.asSharedFlow()

    /**
     * Emits a captured network transaction to live subscribers.
     */
    public fun recordTransaction(transaction: HttpTransaction) {
        scope.launch {
            _liveTransactions.emit(transaction)
        }
    }
}
