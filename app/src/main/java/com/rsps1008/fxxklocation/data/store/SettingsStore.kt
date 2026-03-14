package com.rsps1008.fxxklocation.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    companion object {
        val ENABLE_DRIFT = booleanPreferencesKey("enable_drift")
        val DRIFT_RADIUS = doublePreferencesKey("drift_radius")
        val USE_GOOGLE_PLAY_SERVICES = booleanPreferencesKey("use_google_play_services")
    }

    val enableDrift: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_DRIFT] ?: false
    }

    val driftRadius: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[DRIFT_RADIUS] ?: 10.0
    }

    val useGooglePlayServices: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_GOOGLE_PLAY_SERVICES] ?: true
    }

    suspend fun setEnableDrift(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_DRIFT] = enabled
        }
    }

    suspend fun setDriftRadius(radius: Double) {
        context.dataStore.edit { preferences ->
            preferences[DRIFT_RADIUS] = radius
        }
    }

    suspend fun setUseGooglePlayServices(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_GOOGLE_PLAY_SERVICES] = enabled
        }
    }
}
