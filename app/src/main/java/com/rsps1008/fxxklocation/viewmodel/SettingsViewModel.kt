package com.rsps1008.fxxklocation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rsps1008.fxxklocation.data.store.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)

    val enableDrift: StateFlow<Boolean> = settingsStore.enableDrift
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val driftRadius: StateFlow<Double> = settingsStore.driftRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10.0)

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
}
