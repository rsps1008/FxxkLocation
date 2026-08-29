package com.rsps1008.fxxklocation.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rsps1008.fxxklocation.R
import com.rsps1008.fxxklocation.data.model.LocationData
import com.rsps1008.fxxklocation.data.state.MockLocationRuntimeState
import com.rsps1008.fxxklocation.data.store.SettingsStore
import com.rsps1008.fxxklocation.service.MockLocationService
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settingsStore = SettingsStore(context)

    var selectedLocation by mutableStateOf(LocationData(25.0330, 121.5654)) // Default to Taipei 101
        private set

    var hasLoadedInitialSelectedLocation by mutableStateOf(false)
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

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val _cameraLocations = MutableSharedFlow<Location>(extraBufferCapacity = 1)
    val cameraLocations = _cameraLocations.asSharedFlow()

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    init {
        checkStatus()
        loadLastLocation()
        syncMockingStatus()
        syncCurrentLocation()
        refreshCurrentLocationSnapshot()
        checkAutoStart()
    }

    private fun checkAutoStart() {
        viewModelScope.launch {
            val autoStart = settingsStore.autoStartOnLaunch.first()
            if (autoStart) {
                // Wait for checkStatus to complete and ensure all permissions/settings are OK
                delay(1000) 
                if (_isMockAppSet.value && _hasPermission.value && _isIgnoringBatteryOptimizations.value && _hasNotificationPermission.value) {
                    startMock()
                }
            }
        }
    }

    private fun syncMockingStatus() {
        viewModelScope.launch {
            var previousMocking = false
            settingsStore.isMocking.collect { mocking ->
                _isMocking.value = mocking
                if (!mocking) {
                    _isApplied.value = false
                }
                if (previousMocking && !mocking) {
                    refreshRealLocationAfterMockStopped()
                }
                previousMocking = mocking
            }
        }
    }

    private fun loadLastLocation() {
        viewModelScope.launch {
            val lastLat = settingsStore.lastLatitude.first()
            val lastLng = settingsStore.lastLongitude.first()
            if (lastLat != null && lastLng != null) {
                selectedLocation = selectedLocation.copy(latitude = lastLat, longitude = lastLng)
            }
            hasLoadedInitialSelectedLocation = true

            // If "use real altitude" is enabled, refresh it on start
            if (settingsStore.useRealAltitude.first()) {
                refreshRealAltitude()
            }
        }

        // Observe altitude updates from settings (manual or real)
        viewModelScope.launch {
            settingsStore.lastAltitudeValue.collect { alt ->
                selectedLocation = selectedLocation.copy(altitude = alt)
            }
        }
    }

    private fun syncCurrentLocation() {
        viewModelScope.launch {
            settingsStore.currentLocation.collect { location ->
                // While mocking, the in-process runtime state is fresher than the
                // periodic DataStore snapshot. DataStore remains the fallback for
                // process/UI recreation when no live service state is available.
                if (MockLocationRuntimeState.currentLocation.value == null) {
                    location?.let {
                        _currentLocation.value = it
                    }
                }
            }
        }

        viewModelScope.launch {
            MockLocationRuntimeState.currentLocation.collect { location ->
                location?.let { _currentLocation.value = it }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshCurrentLocationSnapshot() {
        if (!_hasPermission.value || !_isGpsEnabled.value) return

        viewModelScope.launch {
            if (settingsStore.isMocking.first()) return@launch

            val useFLP = settingsStore.useGooglePlayServices.first()
            if (useFLP) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            location?.let { updateCurrentLocation(it) }
                        }.addOnFailureListener {
                            applyBestLastKnownCurrentLocation()
                        }
                } catch (e: SecurityException) {
                    applyBestLastKnownCurrentLocation()
                }
            } else {
                applyBestLastKnownCurrentLocation()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun applyBestLastKnownCurrentLocation() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null

            for (provider in providers) {
                val location = try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                } ?: continue

                if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                    bestLocation = location
                }
            }

            bestLocation?.let { updateCurrentLocation(it) }
        } catch (_: Exception) {
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
            if (MockLocationRuntimeState.isStopRequested.value) return@launch

            val lastLat = settingsStore.lastLatitude.first() ?: return@launch
            val lastLng = settingsStore.lastLongitude.first() ?: return@launch
            val lastAlt = settingsStore.lastAltitudeValue.first()
            if (MockLocationRuntimeState.isStopRequested.value) return@launch
            
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

    private fun updateCurrentLocation(location: Location) {
        // A real-location callback may have been queued before mocking started.
        // Do not let it overwrite the live mock state or its durable snapshot.
        if (MockLocationRuntimeState.currentLocation.value != null) return

        val currentLocation = LocationData(location.latitude, location.longitude, location.altitude)
        _currentLocation.value = currentLocation
        viewModelScope.launch {
            if (MockLocationRuntimeState.currentLocation.value == null) {
                settingsStore.setCurrentLocation(currentLocation)
            }
        }
    }

    fun checkStatus() {
        _hasPermission.value = SystemCheckUtil.hasLocationPermission(context)
        _isGpsEnabled.value = SystemCheckUtil.isGpsEnabled(context)
        _isMockAppSet.value = SystemCheckUtil.isMockLocationEnabled(context)
        _hasNotificationPermission.value = SystemCheckUtil.hasNotificationPermission(context)
        _isIgnoringBatteryOptimizations.value = SystemCheckUtil.isIgnoringBatteryOptimizations(context)
    }

    fun refreshStatusAfterTransition() {
        viewModelScope.launch {
            delay(300)
            checkStatus()
        }
    }

    fun refreshBatteryOptimizationStatusAfterTransition() {
        viewModelScope.launch {
            delay(1200)
            _isIgnoringBatteryOptimizations.value = SystemCheckUtil.isIgnoringBatteryOptimizations(context)
        }
    }

    fun updateSelectedLocation(lat: Double, lng: Double) {
        selectedLocation = selectedLocation.copy(latitude = lat, longitude = lng)
        _isApplied.value = false
        viewModelScope.launch {
            settingsStore.setLastLocation(lat, lng)
        }
    }

    fun searchPlace(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            _messages.tryEmit(R.string.search_place_empty)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { searchPlaceOnNominatim(trimmedQuery) }
            val location = result.getOrNull()

            when {
                location != null -> emitCameraLocation(location)
                result.isFailure -> _messages.tryEmit(R.string.search_place_failed)
                else -> _messages.tryEmit(R.string.search_place_not_found)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun centerMapOnCurrentLocation() {
        if (!_hasPermission.value) {
            _messages.tryEmit(R.string.location_permission_required)
            return
        }
        if (!_isGpsEnabled.value) {
            _messages.tryEmit(R.string.enable_gps_first)
            return
        }

        viewModelScope.launch {
            val isMocking = settingsStore.isMocking.first()
            if (isMocking) {
                val currentMockLocation = _currentLocation.value
                    ?: settingsStore.currentLocation.first()
                    ?: return@launch

                val mockedLocation = Location("mock").apply {
                    latitude = currentMockLocation.latitude
                    longitude = currentMockLocation.longitude
                    altitude = currentMockLocation.altitude
                }
                emitCameraLocation(mockedLocation)
                return@launch
            }

            val useFLP = settingsStore.useGooglePlayServices.first()
            requestCurrentLocationForCamera(useFLP = useFLP)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocationForCamera(useFLP: Boolean) {
        if (useFLP) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            applyLocatedPosition(
                                location,
                                successMessageRes = null,
                                updateSelectedTarget = false,
                                updateAltitudeFromLocation = false
                            )
                        } else {
                            applyBestLastKnownLocation(
                                emitFailureMessage = true,
                                successMessageRes = null,
                                updateSelectedTarget = false,
                                updateAltitudeFromLocation = false,
                                resumeMockAfterLocate = false
                            )
                        }
                    }
                    .addOnFailureListener {
                        applyBestLastKnownLocation(
                            emitFailureMessage = true,
                            successMessageRes = null,
                            updateSelectedTarget = false,
                            updateAltitudeFromLocation = false,
                            resumeMockAfterLocate = false
                        )
                    }
            } catch (_: SecurityException) {
                applyBestLastKnownLocation(
                    emitFailureMessage = true,
                    successMessageRes = null,
                    updateSelectedTarget = false,
                    updateAltitudeFromLocation = false,
                    resumeMockAfterLocate = false
                )
            }
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                null,
                context.mainExecutor
            ) { location ->
                if (location != null) {
                    applyLocatedPosition(
                        location,
                        successMessageRes = null,
                        updateSelectedTarget = false,
                        updateAltitudeFromLocation = false
                    )
                } else {
                    applyBestLastKnownLocation(
                        emitFailureMessage = true,
                        successMessageRes = null,
                        updateSelectedTarget = false,
                        updateAltitudeFromLocation = false,
                        resumeMockAfterLocate = false
                    )
                }
            }
        } catch (_: Exception) {
            applyBestLastKnownLocation(
                emitFailureMessage = true,
                successMessageRes = null,
                updateSelectedTarget = false,
                updateAltitudeFromLocation = false,
                resumeMockAfterLocate = false
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun locateFromLastKnownLocation(
        fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null,
        emitFailureMessage: Boolean = true,
        @StringRes successMessageRes: Int? = null,
        updateSelectedTarget: Boolean = false,
        updateAltitudeFromLocation: Boolean = false,
        resumeMockAfterLocate: Boolean = false
    ) {
        fusedLocationClient?.lastLocation
            ?.addOnSuccessListener { location ->   
                if (location != null) {
                    applyLocatedPosition(location, successMessageRes, updateSelectedTarget, updateAltitudeFromLocation)
                    if (resumeMockAfterLocate) resumeMockAfterRefresh()
                } else {
                    applyBestLastKnownLocation(
                        emitFailureMessage,
                        successMessageRes,
                        updateSelectedTarget,
                        updateAltitudeFromLocation,
                        resumeMockAfterLocate
                    )
                }
            }
            ?.addOnFailureListener {
                applyBestLastKnownLocation(
                    emitFailureMessage,
                    successMessageRes,
                    updateSelectedTarget,
                    updateAltitudeFromLocation,
                    resumeMockAfterLocate
                )
            }
            ?: applyBestLastKnownLocation(
                emitFailureMessage,
                successMessageRes,
                updateSelectedTarget,
                updateAltitudeFromLocation,
                resumeMockAfterLocate
            )
    }

    @SuppressLint("MissingPermission")
    private fun applyBestLastKnownLocation(
        emitFailureMessage: Boolean = true,
        @StringRes successMessageRes: Int? = null,
        updateSelectedTarget: Boolean = false,
        updateAltitudeFromLocation: Boolean = false,
        resumeMockAfterLocate: Boolean = false
    ) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null

        for (provider in providers) {
            val location = try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } ?: continue

            if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                bestLocation = location
            }
        }

        if (bestLocation != null) {
            applyLocatedPosition(bestLocation, successMessageRes, updateSelectedTarget, updateAltitudeFromLocation)
            if (resumeMockAfterLocate) resumeMockAfterRefresh()
        } else if (emitFailureMessage) {
            _messages.tryEmit(R.string.unable_to_get_current_location)
            if (resumeMockAfterLocate) resumeMockAfterRefresh()
        }
    }

    private fun applyLocatedPosition(
        location: Location,
        @StringRes successMessageRes: Int? = null,
        updateSelectedTarget: Boolean = false,
        updateAltitudeFromLocation: Boolean = false
    ) {
        // Ignore real-location callbacks that were queued before mocking began;
        // they must not move the camera away from the active mock position.
        if (MockLocationRuntimeState.currentLocation.value != null) return

        emitCameraLocation(location)
        updateCurrentLocation(location)
        if (updateSelectedTarget) {
            updateSelectedLocation(location.latitude, location.longitude)
        }
        if (updateSelectedTarget || updateAltitudeFromLocation) {
            updateAltitude(location.altitude)
        }
        if (successMessageRes != null) {
            _messages.tryEmit(successMessageRes)
        }
    }

    private fun emitCameraLocation(location: Location) {
        _cameraLocations.tryEmit(location)
    }

    private fun searchPlaceOnNominatim(query: String): Location? {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = URL(
            "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=jsonv2&limit=1"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "${context.packageName}/1.0 (Fake Location)"
            )
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
        }

        return try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val firstPlace = JSONArray(response).optJSONObject(0) ?: return null
            val latitude = firstPlace.optString("lat").toDoubleOrNull() ?: return null
            val longitude = firstPlace.optString("lon").toDoubleOrNull() ?: return null
            Location("nominatim").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
        } catch (e: Exception) {
            if (e is IOException || e is SecurityException) {
                throw e
            }
            throw IOException("Failed to search place", e)
        } finally {
            connection.disconnect()
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
        _isApplied.value = true
    }

    fun stopMock() {
        MockLocationRuntimeState.requestStop()
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP_MOCK
        }
        context.startService(intent)
        // Let the service atomically persist its final location and clear the
        // mocking flag. Writing the flag here could race that final snapshot.
        _isApplied.value = false
    }

    @SuppressLint("MissingPermission")
    private fun refreshRealLocationAfterMockStopped() {
        if (!_hasPermission.value || !_isGpsEnabled.value) return

        viewModelScope.launch {
            val useFLP = settingsStore.useGooglePlayServices.first()
            if (useFLP) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                applyLocatedPosition(
                                    location = location,
                                    successMessageRes = null,
                                    updateSelectedTarget = false,
                                    updateAltitudeFromLocation = true
                                )
                            } else {
                                applyBestLastKnownLocation(
                                    emitFailureMessage = false,
                                    successMessageRes = null,
                                    updateSelectedTarget = false,
                                    updateAltitudeFromLocation = true,
                                    resumeMockAfterLocate = false
                                )
                            }
                        }.addOnFailureListener {
                            applyBestLastKnownLocation(
                                emitFailureMessage = false,
                                successMessageRes = null,
                                updateSelectedTarget = false,
                                updateAltitudeFromLocation = true,
                                resumeMockAfterLocate = false
                            )
                        }
                } catch (e: SecurityException) {
                    applyBestLastKnownLocation(
                        emitFailureMessage = false,
                        successMessageRes = null,
                        updateSelectedTarget = false,
                        updateAltitudeFromLocation = true,
                        resumeMockAfterLocate = false
                    )
                }
            } else {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                try {
                    locationManager.getCurrentLocation(
                        LocationManager.GPS_PROVIDER,
                        null,
                        context.mainExecutor
                    ) { location ->
                        if (location != null) {
                            applyLocatedPosition(
                                location = location,
                                successMessageRes = null,
                                updateSelectedTarget = false,
                                updateAltitudeFromLocation = true
                            )
                        } else {
                            applyBestLastKnownLocation(
                                emitFailureMessage = false,
                                successMessageRes = null,
                                updateSelectedTarget = false,
                                updateAltitudeFromLocation = true,
                                resumeMockAfterLocate = false
                            )
                        }
                    }
                } catch (e: Exception) {
                    applyBestLastKnownLocation(
                        emitFailureMessage = false,
                        successMessageRes = null,
                        updateSelectedTarget = false,
                        updateAltitudeFromLocation = true,
                        resumeMockAfterLocate = false
                    )
                }
            }
        }
    }
}
