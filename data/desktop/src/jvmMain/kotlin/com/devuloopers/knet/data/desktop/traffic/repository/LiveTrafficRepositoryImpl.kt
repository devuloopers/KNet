package com.devuloopers.knet.data.desktop.traffic.repository

import com.devuloopers.knet.data.desktop.mapper.TransactionMapper
import com.devuloopers.knet.domain.network.model.HttpTransaction
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Desktop implementation of [LiveTrafficRepository].
 */
public class LiveTrafficRepositoryImpl(
    private val database: KNetDatabase
) : LiveTrafficRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    override val transactionsFlow: Flow<List<HttpTransaction>> = database.httpTransactionDao().getAllTransactionsFlow().map { entities ->
        entities.map { TransactionMapper.mapEntityToDomain(it) }
    }

    override fun clearSession() {
        scope.launch {
            database.httpTransactionDao().clearAll()
        }
    }
}
