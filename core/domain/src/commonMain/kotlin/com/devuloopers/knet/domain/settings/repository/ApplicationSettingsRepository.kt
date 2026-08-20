package com.devuloopers.knet.domain.settings.repository

import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import kotlinx.coroutines.flow.Flow

/**
 * Persists process-level application settings independently from workspace layout state.
 *
 * Implementations must apply [update] atomically against the latest persisted value so independent feature
 * writers cannot overwrite unrelated fields with stale snapshots.
 */
interface ApplicationSettingsRepository {
    /** Reactive stream of the latest validated application settings. */
    val settings: Flow<ApplicationSettings>

    /**
     * Atomically transforms the latest persisted settings.
     *
     * @param transform Pure transformation applied to the current persisted value.
     */
    suspend fun update(transform: (ApplicationSettings) -> ApplicationSettings)
}
