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

    private fun refreshRealAltitude() {
        if (!_hasPermission.value) return
        
        viewModelScope.launch {
            val wasMocking = _isMocking.value
            if (wasMocking) {
                val pauseIntent = Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_PAUSE_MOCK
                }
                context.startService(pauseIntent)
                delay(500) 
            }

            val useFLP = settingsStore.useGooglePlayServices.first()
            if (useFLP) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            updateAltitude(it.altitude)
                        }
                        resumeMockIfNeeded(wasMocking)
                    }.addOnFailureListener {
                        resumeMockIfNeeded(wasMocking)
                    }
                } catch (e: SecurityException) {
                    resumeMockIfNeeded(wasMocking)
                }
            } else {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                try {
                    val providers = locationManager.getProviders(true)
                    var bestAltitude = 3.0
                    var found = false
                    for (provider in providers) {
                        val l = locationManager.getLastKnownLocation(provider) ?: continue
                        bestAltitude = l.altitude
                        found = true
                        break
                    }
                    if (found) {
                        updateAltitude(bestAltitude)
                    }
                } catch (e: SecurityException) {
                }
                resumeMockIfNeeded(wasMocking)
            }
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
            val wasMocking = _isMocking.value
            val useFLP = settingsStore.useGooglePlayServices.first()
            
            // 1. Temporarily pause mock if active
            if (wasMocking) {
                val pauseIntent = Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_PAUSE_MOCK
                }
                context.startService(pauseIntent)
                delay(500) // Brief delay to let provider removal take effect
            }
            
            if (useFLP) {
                // 2a. Get real location using FusedLocationProviderClient
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    processNewLocation(location, wasMocking)
                }.addOnFailureListener {
                    resumeMockIfNeeded(wasMocking)
                }
            } else {
                // 2b. Get real location using traditional LocationManager
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val providers = locationManager.getProviders(true)
                var bestLocation: android.location.Location? = null
                
                for (provider in providers) {
                    val l = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                        bestLocation = l
                    }
                }
                processNewLocation(bestLocation, wasMocking)
            }
        }
    }

    private fun processNewLocation(location: android.location.Location?, wasMocking: Boolean) {
        location?.let {
            updateSelectedLocation(it.latitude, it.longitude)
            val altitudeToUse = it.altitude
            updateAltitude(altitudeToUse)
            
            // 3. Resume mock with NEW location if it was active
            if (wasMocking) {
                val resumeIntent = Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_MOCK
                    putExtra(MockLocationService.EXTRA_LATITUDE, it.latitude)
                    putExtra(MockLocationService.EXTRA_LONGITUDE, it.longitude)
                    putExtra(MockLocationService.EXTRA_ALTITUDE, altitudeToUse)
                }
                context.startService(resumeIntent)
            }
        } ?: run {
            resumeMockIfNeeded(wasMocking)
        }
    }

    private fun resumeMockIfNeeded(wasMocking: Boolean) {
        if (wasMocking) {
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
    }

    fun startMock() {
        if (!_isMockAppSet.value || !_hasPermission.value || !_isIgnoringBatteryOptimizations.value) return
        
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
