package com.devuloopers.knet.data.desktop.workspace.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.devuloopers.knet.application.contract.device.DeviceWorkspacePreferences
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.devuloopers.knet.identity.RegisteredDeviceId

/** Stores non-secret tab selection and visibility in the existing desktop preference store. */
class DeviceWorkspacePreferencesAdapter(private val dataStore: DataStore<Preferences>) : DeviceWorkspacePreferences {
    override val selectedSource: Flow<TrafficSource> = dataStore.data.map { preferences ->
        TrafficSource.fromPreference(preferences[KEY]).takeUnless { source -> source == TrafficSource.All } ?: TrafficSource.Other
    }
    override suspend fun select(source: TrafficSource) { dataStore.edit { preferences -> preferences[KEY] = source.preferenceKey } }
    override val openDevices: Flow<Set<RegisteredDeviceId>> = dataStore.data.map { preferences ->
        preferences[OPEN_DEVICES].orEmpty().mapTo(mutableSetOf()) { RegisteredDeviceId(it) }
    }
    override suspend fun setOpen(id: RegisteredDeviceId, open: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[OPEN_DEVICES].orEmpty()
            preferences[OPEN_DEVICES] = if (open) current + id.value else current - id.value
        }
    }
    private companion object {
        val KEY = stringPreferencesKey("deviceWorkspace.selectedSource")
        val OPEN_DEVICES = stringSetPreferencesKey("deviceWorkspace.openDevices")
    }
}
