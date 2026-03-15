package com.rsps1008.fxxklocation.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rsps1008.fxxklocation.data.store.SettingsStore
import com.rsps1008.fxxklocation.service.MockLocationService
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settingsStore = SettingsStore(application)

    val enableDrift: StateFlow<Boolean> = settingsStore.enableDrift
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val driftRadius: StateFlow<Double> = settingsStore.driftRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10.0)

    val useGooglePlayServices: StateFlow<Boolean> = settingsStore.useGooglePlayServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val useRealAltitude: StateFlow<Boolean> = settingsStore.useRealAltitude
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastAltitude: StateFlow<Double> = settingsStore.lastAltitudeValue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3.0)

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled = _isGpsEnabled.asStateFlow()

    private val _isMockAppSet = MutableStateFlow(false)
    val isMockAppSet = _isMockAppSet.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission = _hasNotificationPermission.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.asStateFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        _hasPermission.value = SystemCheckUtil.hasLocationPermission(context)
        _isGpsEnabled.value = SystemCheckUtil.isGpsEnabled(context)
        _isMockAppSet.value = SystemCheckUtil.isMockLocationEnabled(context)
        _hasNotificationPermission.value = SystemCheckUtil.hasNotificationPermission(context)
        _isIgnoringBatteryOptimizations.value = SystemCheckUtil.isIgnoringBatteryOptimizations(context)
        
        viewModelScope.launch {
            if (settingsStore.useRealAltitude.first()) {
                refreshAltitude()
            }
        }
    }

    fun setEnableDrift(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setEnableDrift(enabled)
        }
    }

    fun setDriftRadius(radius: Double) {
        viewModelScope.launch {
            settingsStore.setDriftRadius(radius)
        }
    }

    fun setUseGooglePlayServices(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setUseGooglePlayServices(enabled)
        }
    }

    fun setUseRealAltitude(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setUseRealAltitude(enabled)
            if (enabled) {
                refreshAltitude()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshAltitude() {
        if (!_hasPermission.value) return
        
        viewModelScope.launch {
            val wasMocking = settingsStore.isMocking.first()
            if (wasMocking) {
                // To get real location, we need to pause mock (remove test provider)
                val pauseIntent = Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_PAUSE_MOCK
                }
                context.startService(pauseIntent)
                delay(1000) // Brief delay to let provider removal take effect
            }

            val useFLP = settingsStore.useGooglePlayServices.first()
            if (useFLP) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            location?.let {
                                saveAltitude(it.altitude)
                            }
                            if (wasMocking) resumeMock()
                        }.addOnFailureListener {
                            if (wasMocking) resumeMock()
                        }
                } catch (e: SecurityException) {
                    if (wasMocking) resumeMock()
                }
            } else {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                try {
                    locationManager.getCurrentLocation(
                        android.location.LocationManager.GPS_PROVIDER,
                        null,
                        context.mainExecutor
                    ) { location ->
                        location?.let {
                            saveAltitude(it.altitude)
                        }
                        if (wasMocking) resumeMock()
                    }
                } catch (e: Exception) {
                    // Fallback to last known if getCurrentLocation fails
                    try {
                        val providers = locationManager.getProviders(true)
                        for (provider in providers) {
                            val l = locationManager.getLastKnownLocation(provider) ?: continue
                            saveAltitude(l.altitude)
                            break
                        }
                    } catch (e: SecurityException) {}
                    if (wasMocking) resumeMock()
                }
            }
        }
    }

    private fun saveAltitude(altitude: Double) {
        viewModelScope.launch {
            settingsStore.setLastAltitudeValue(altitude)
        }
    }

    private fun resumeMock() {
        viewModelScope.launch {
            val isMocking = settingsStore.isMocking.first()
            if (!isMocking) return@launch

            val lat = settingsStore.lastLatitude.first() ?: return@launch
            val lng = settingsStore.lastLongitude.first() ?: return@launch
            val alt = settingsStore.lastAltitudeValue.first()
            
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_MOCK
                putExtra(MockLocationService.EXTRA_LATITUDE, lat)
                putExtra(MockLocationService.EXTRA_LONGITUDE, lng)
                putExtra(MockLocationService.EXTRA_ALTITUDE, alt)
            }
            context.startForegroundService(intent)
        }
    }
}
