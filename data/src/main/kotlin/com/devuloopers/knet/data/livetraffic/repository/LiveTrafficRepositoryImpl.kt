package com.devuloopers.knet.data.livetraffic.repository

import com.devuloopers.knet.data.repository.KNetCoreRepository
import com.devuloopers.knet.domain.livetraffic.repository.LiveTrafficRepository
import com.devuloopers.knet.model.HttpTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Production implementation of [LiveTrafficRepository] delegating to the offline data orchestrator [KNetCoreRepository].
 *
 * @property coreRepository The orchestrator managing Room database persistence and proxy socket ingress.
 */
class LiveTrafficRepositoryImpl(
    private val coreRepository: KNetCoreRepository
) : LiveTrafficRepository {

    override val transactionsFlow: Flow<List<HttpTransaction>>
        get() = coreRepository.transactionsFlow

    override fun clearSession() {
        coreRepository.clearSession()
    }
}
