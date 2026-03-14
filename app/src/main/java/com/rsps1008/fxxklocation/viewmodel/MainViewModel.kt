package com.rsps1008.fxxklocation.viewmodel

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rsps1008.fxxklocation.data.model.LocationData
import com.rsps1008.fxxklocation.service.MockLocationService
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

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
