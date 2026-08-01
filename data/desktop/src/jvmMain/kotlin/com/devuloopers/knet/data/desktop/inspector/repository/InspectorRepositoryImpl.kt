package com.devuloopers.knet.data.desktop.inspector.repository

import com.devuloopers.knet.data.desktop.mapper.TransactionMapper
import com.devuloopers.knet.domain.inspector.repository.InspectorRepository
import com.devuloopers.knet.domain.network.model.HttpTransaction
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Desktop implementation of [InspectorRepository].
 */
public class InspectorRepositoryImpl(
    private val database: KNetDatabase
) : InspectorRepository {

    override fun getTransactionById(transactionId: String): Flow<HttpTransaction?> {
        return database.httpTransactionDao().getAllTransactionsFlow().map { entities ->
            val entity = entities.firstOrNull { it.id == transactionId } ?: return@map null
            TransactionMapper.mapEntityToDomain(entity)
        }
    }
}
