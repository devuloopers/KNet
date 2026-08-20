package com.devuloopers.knet.domain.settings.usecase

import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import kotlinx.coroutines.flow.Flow

/** Provides the reactive process-level application settings stream. */
class ObserveApplicationSettingsUseCase(
    private val repository: ApplicationSettingsRepository,
) {
    /** Returns settings updates emitted by the persistence boundary. */
    fun execute(): Flow<ApplicationSettings> = repository.settings
}

/** Applies an atomic transformation to process-level application settings. */
class UpdateApplicationSettingsUseCase(
    private val repository: ApplicationSettingsRepository,
) {
    /**
     * Applies [transform] to the latest persisted settings.
     *
     * @param transform Pure transformation producing a validated settings value.
     */
    suspend fun execute(transform: (ApplicationSettings) -> ApplicationSettings) {
        repository.update(transform)
    }
}
