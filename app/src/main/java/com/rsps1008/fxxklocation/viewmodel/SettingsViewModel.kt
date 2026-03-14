package com.rsps1008.fxxklocation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rsps1008.fxxklocation.data.store.SettingsStore
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
