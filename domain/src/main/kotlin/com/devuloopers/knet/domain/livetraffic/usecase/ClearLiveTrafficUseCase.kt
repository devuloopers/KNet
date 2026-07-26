package com.devuloopers.knet.domain.livetraffic.usecase

import com.devuloopers.knet.domain.livetraffic.repository.LiveTrafficRepository

/**
 * UseCase executing session clearing for the live traffic feature.
 */
class ClearLiveTrafficUseCase(
    private val repository: LiveTrafficRepository
) {
    /**
     * Clears captured transaction data from the repository session.
     */
    fun execute() {
        repository.clearSession()
    }
}
