package com.rsps1008.fxxklocation.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        val USE_REAL_ALTITUDE = booleanPreferencesKey("use_real_altitude")
        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        val LAST_ALTITUDE_VALUE = doublePreferencesKey("last_altitude_value")
        val IS_MOCKING = booleanPreferencesKey("is_mocking")
        val ENABLE_AUTO_STOP = booleanPreferencesKey("enable_auto_stop")
        val AUTO_STOP_MINUTES = intPreferencesKey("auto_stop_minutes")
        val AUTO_START_ON_LAUNCH = booleanPreferencesKey("auto_start_on_launch")
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

    val useRealAltitude: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_REAL_ALTITUDE] ?: false
    }

    val lastLatitude: Flow<Double?> = context.dataStore.data.map { preferences ->
        preferences[LAST_LATITUDE]
    }

    val lastLongitude: Flow<Double?> = context.dataStore.data.map { preferences ->
        preferences[LAST_LONGITUDE]
    }

    val lastAltitudeValue: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[LAST_ALTITUDE_VALUE] ?: 3.0
    }

    val isMocking: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_MOCKING] ?: false
    }

    val enableAutoStop: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_AUTO_STOP] ?: false
    }

    val autoStopMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AUTO_STOP_MINUTES] ?: 30
    }

    val autoStartOnLaunch: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_START_ON_LAUNCH] ?: false
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

    suspend fun setUseRealAltitude(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_REAL_ALTITUDE] = enabled
        }
    }

    suspend fun setLastLocation(lat: Double, lng: Double) {
        context.dataStore.edit { preferences ->
            preferences[LAST_LATITUDE] = lat
            preferences[LAST_LONGITUDE] = lng
        }
    }

    suspend fun setLastAltitudeValue(altitude: Double) {
        context.dataStore.edit { preferences ->
            preferences[LAST_ALTITUDE_VALUE] = altitude
        }
    }

    suspend fun setIsMocking(mocking: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_MOCKING] = mocking
        }
    }

    suspend fun setEnableAutoStop(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_AUTO_STOP] = enabled
        }
    }

    suspend fun setAutoStopMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_STOP_MINUTES] = minutes
        }
    }

    suspend fun setAutoStartOnLaunch(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_START_ON_LAUNCH] = enabled
        }
    }
}
