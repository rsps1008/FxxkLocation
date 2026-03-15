package com.rsps1008.fxxklocation.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rsps1008.fxxklocation.data.model.LocationData
import com.rsps1008.fxxklocation.data.store.SettingsStore
import com.rsps1008.fxxklocation.service.MockLocationService
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settingsStore = SettingsStore(context)

    var selectedLocation by mutableStateOf(LocationData(25.0330, 121.5654)) // Default to Taipei 101
        private set

    private val _isMocking = MutableStateFlow(false)
    val isMocking = _isMocking.asStateFlow()

    private val _isApplied = MutableStateFlow(false)
    val isApplied = _isApplied.asStateFlow()

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
        loadLastLocation()
        syncMockingStatus()
    }

    private fun syncMockingStatus() {
        viewModelScope.launch {
            settingsStore.isMocking.collect { mocking ->
                _isMocking.value = mocking
                if (!mocking) {
                    _isApplied.value = false
                }
            }
        }
    }

    private fun loadLastLocation() {
        viewModelScope.launch {
            val lastLat = settingsStore.lastLatitude.first()
            val lastLng = settingsStore.lastLongitude.first()
            val lastAlt = settingsStore.lastAltitudeValue.first()
            if (lastLat != null && lastLng != null) {
                selectedLocation = LocationData(lastLat, lastLng, lastAlt)
            }

            // If "use real altitude" is enabled, refresh it on start
            if (settingsStore.useRealAltitude.first()) {
                refreshRealAltitude()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshRealAltitude() {
        if (!_hasPermission.value) return
        
        viewModelScope.launch {
            val wasMocking = _isMocking.value
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
                                updateAltitude(it.altitude)
                            }
                            if (wasMocking) resumeMockAfterRefresh()
                        }.addOnFailureListener {
                            if (wasMocking) resumeMockAfterRefresh()
                        }
                } catch (e: SecurityException) {
                    if (wasMocking) resumeMockAfterRefresh()
                }
            } else {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                try {
                    locationManager.getCurrentLocation(
                        android.location.LocationManager.GPS_PROVIDER,
                        null,
                        context.mainExecutor
                    ) { location ->
                        location?.let {
                            updateAltitude(it.altitude)
                        }
                        if (wasMocking) resumeMockAfterRefresh()
                    }
                } catch (e: Exception) {
                    // Fallback to last known if getCurrentLocation fails
                    try {
                        val providers = locationManager.getProviders(true)
                        for (provider in providers) {
                            val l = locationManager.getLastKnownLocation(provider) ?: continue
                            updateAltitude(l.altitude)
                            break
                        }
                    } catch (e: SecurityException) {}
                    if (wasMocking) resumeMockAfterRefresh()
                }
            }
        }
    }

    private fun resumeMockAfterRefresh() {
        viewModelScope.launch {
            val lastLat = settingsStore.lastLatitude.first() ?: return@launch
            val lastLng = settingsStore.lastLongitude.first() ?: return@launch
            val lastAlt = settingsStore.lastAltitudeValue.first()
            
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_MOCK
                putExtra(MockLocationService.EXTRA_LATITUDE, lastLat)
                putExtra(MockLocationService.EXTRA_LONGITUDE, lastLng)
                putExtra(MockLocationService.EXTRA_ALTITUDE, lastAlt)
            }
            context.startForegroundService(intent)
        }
    }

    private fun updateAltitude(altitude: Double) {
        selectedLocation = selectedLocation.copy(altitude = altitude)
        viewModelScope.launch {
            settingsStore.setLastAltitudeValue(altitude)
        }
    }

    fun checkStatus() {
        _hasPermission.value = SystemCheckUtil.hasLocationPermission(context)
        _isGpsEnabled.value = SystemCheckUtil.isGpsEnabled(context)
        _isMockAppSet.value = SystemCheckUtil.isMockLocationEnabled(context)
        _hasNotificationPermission.value = SystemCheckUtil.hasNotificationPermission(context)
        _isIgnoringBatteryOptimizations.value = SystemCheckUtil.isIgnoringBatteryOptimizations(context)
    }

    fun updateSelectedLocation(lat: Double, lng: Double) {
        selectedLocation = selectedLocation.copy(latitude = lat, longitude = lng)
        _isApplied.value = false
        viewModelScope.launch {
            settingsStore.setLastLocation(lat, lng)
        }
    }

    @SuppressLint("MissingPermission")
    fun locateMe() {
        if (!_hasPermission.value) return
        
        viewModelScope.launch {
            // 1. Forcibly stop mock (equivalent to clicking X)
            stopMock()
            delay(1000) // Give it some time to clear mock provider
            
            val useFLP = settingsStore.useGooglePlayServices.first()
            
            if (useFLP) {
                // 2a. Get real location using FusedLocationProviderClient (forcing refresh)
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        location?.let {
                            updateSelectedLocation(it.latitude, it.longitude)
                            updateAltitude(it.altitude)
                        }
                    }
            } else {
                // 2b. Get real location using traditional LocationManager
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                try {
                    locationManager.getCurrentLocation(
                        android.location.LocationManager.GPS_PROVIDER,
                        null,
                        context.mainExecutor
                    ) { location ->
                        location?.let {
                            updateSelectedLocation(it.latitude, it.longitude)
                            updateAltitude(it.altitude)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to last known if getCurrentLocation fails
                    val providers = locationManager.getProviders(true)
                    var bestLocation: android.location.Location? = null
                    for (provider in providers) {
                        val l = locationManager.getLastKnownLocation(provider) ?: continue
                        if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                            bestLocation = l
                        }
                    }
                    bestLocation?.let {
                        updateSelectedLocation(it.latitude, it.longitude)
                        updateAltitude(it.altitude)
                    }
                }
            }
        }
    }


    fun startMock() {
        if (!_isMockAppSet.value || !_hasPermission.value || !_isIgnoringBatteryOptimizations.value || !_hasNotificationPermission.value) return
        
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_MOCK
            putExtra(MockLocationService.EXTRA_LATITUDE, selectedLocation.latitude)
            putExtra(MockLocationService.EXTRA_LONGITUDE, selectedLocation.longitude)
            putExtra(MockLocationService.EXTRA_ALTITUDE, selectedLocation.altitude)
        }
        context.startForegroundService(intent)
        viewModelScope.launch {
            settingsStore.setIsMocking(true)
        }
        _isApplied.value = true
    }

    fun stopMock() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP_MOCK
        }
        context.startService(intent)
        viewModelScope.launch {
            settingsStore.setIsMocking(false)
        }
        _isApplied.value = false
    }
}
