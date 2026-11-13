package com.devuloopers.knet.data.desktop.workspace.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devuloopers.knet.application.contract.device.DeviceWorkspacePreferences
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores one non-secret tab key in the existing desktop preference store. */
class DeviceWorkspacePreferencesAdapter(private val dataStore: DataStore<Preferences>) : DeviceWorkspacePreferences {
    override val selectedSource: Flow<TrafficSource> = dataStore.data.map { preferences ->
        TrafficSource.fromPreference(preferences[KEY]).takeUnless { source -> source == TrafficSource.All } ?: TrafficSource.Other
    }
    override suspend fun select(source: TrafficSource) { dataStore.edit { preferences -> preferences[KEY] = source.preferenceKey } }
    private companion object { val KEY = stringPreferencesKey("deviceWorkspace.selectedSource") }
}
