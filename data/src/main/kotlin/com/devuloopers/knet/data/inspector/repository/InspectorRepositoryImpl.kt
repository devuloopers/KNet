package com.devuloopers.knet.data.inspector.repository

import com.devuloopers.knet.data.repository.KNetCoreRepository
import com.devuloopers.knet.domain.inspector.repository.InspectorRepository
import com.devuloopers.knet.model.HttpTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Production implementation of [InspectorRepository] delegating queries to [KNetCoreRepository].
 *
 * @property coreRepository Core offline repository orchestrator.
 */
class InspectorRepositoryImpl(
    private val coreRepository: KNetCoreRepository
) : InspectorRepository {

    override fun getTransactionById(transactionId: String): Flow<HttpTransaction?> {
        return coreRepository.transactionsFlow.map { list ->
            list.find { it.id == transactionId }
        }
    }
}
