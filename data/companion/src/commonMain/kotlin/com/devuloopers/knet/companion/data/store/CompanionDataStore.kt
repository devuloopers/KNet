package com.devuloopers.knet.companion.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import okio.Path

/** Creates the process-singleton KMP companion DataStore at a platform-owned application path. */
internal fun createCompanionDataStore(
    path: Path,
    scope: CoroutineScope,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = { path },
)

internal const val COMPANION_DATA_STORE_FILE_NAME: String = "companion.preferences_pb"
