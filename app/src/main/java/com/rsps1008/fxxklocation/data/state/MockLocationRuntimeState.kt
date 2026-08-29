package com.rsps1008.fxxklocation.data.state

import com.rsps1008.fxxklocation.data.model.LocationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process handoff for the location currently being emitted by the mock service.
 *
 * The service still persists a periodic snapshot in DataStore so the latest known
 * location survives UI recreation or process recreation. This state is the fast
 * path used while both the service and the main screen are alive. The stop flag
 * prevents delayed location callbacks from restarting a stop that is in progress.
 */
object MockLocationRuntimeState {
    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation = _currentLocation.asStateFlow()
    private val _isStopRequested = MutableStateFlow(false)
    val isStopRequested = _isStopRequested.asStateFlow()

    fun update(location: LocationData) {
        _currentLocation.value = location
    }

    fun clear() {
        _currentLocation.value = null
    }

    fun requestStop() {
        _isStopRequested.value = true
    }

    fun clearStopRequest() {
        _isStopRequested.value = false
    }
}
