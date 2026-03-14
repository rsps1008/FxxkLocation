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
    }

    fun checkStatus() {
        _hasPermission.value = SystemCheckUtil.hasLocationPermission(context)
        _isGpsEnabled.value = SystemCheckUtil.isGpsEnabled(context)
        _isMockAppSet.value = SystemCheckUtil.isMockLocationEnabled(context)
        _isIgnoringBatteryOptimizations.value = SystemCheckUtil.isIgnoringBatteryOptimizations(context)
    }

    fun updateSelectedLocation(lat: Double, lng: Double) {
        selectedLocation = LocationData(lat, lng)
        _isApplied.value = false
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
            
            // 3. Resume mock with NEW location if it was active
            if (wasMocking) {
                val resumeIntent = Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_MOCK
                    putExtra(MockLocationService.EXTRA_LATITUDE, it.latitude)
                    putExtra(MockLocationService.EXTRA_LONGITUDE, it.longitude)
                }
                context.startService(resumeIntent)
            }
        } ?: run {
            resumeMockIfNeeded(wasMocking)
        }
    }

    private fun resumeMockIfNeeded(wasMocking: Boolean) {
        if (wasMocking) {
            val resumeIntent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_RESUME_MOCK
            }
            context.startService(resumeIntent)
        }
    }

    fun startMock() {
        if (!_isMockAppSet.value || !_hasPermission.value || !_isIgnoringBatteryOptimizations.value) return
        
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_MOCK
            putExtra(MockLocationService.EXTRA_LATITUDE, selectedLocation.latitude)
            putExtra(MockLocationService.EXTRA_LONGITUDE, selectedLocation.longitude)
        }
        context.startForegroundService(intent)
        _isMocking.value = true
        _isApplied.value = true
    }

    fun stopMock() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP_MOCK
        }
        context.startService(intent)
        _isMocking.value = false
        _isApplied.value = false
    }
}
